package org.nms.Database.Models;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import org.nms.ConsoleLogger;
import org.nms.Database.PostgresQuery;

public class ProvisionModel implements BaseModel
{
    private ProvisionModel()
    {
        // Private constructor
    }

    private static final ProvisionModel instance = new ProvisionModel();

    public static ProvisionModel getInstance()
    {
        return instance;
    }

    private String getMeaningfulErrorMessage(Throwable err)
    {
        String message = err.getMessage();
        if (message.contains("23505"))
        {
            return "Duplicate entry detected: A provision profile with this IP already exists.";
        }
        else if (message.contains("23503"))
        {
            return "Foreign key violation: Referenced credential or discovery result does not exist.";
        }
        else
        {
            return "Database error: " + message;
        }
    }

    @Override
    public Future<Void> createSchema()
    {
        var CREATE_PROVISION_PROFILES_TABLE = """
                CREATE TABLE IF NOT EXISTS provision_profiles (
                    id SERIAL PRIMARY KEY,
                    ip VARCHAR(255) UNIQUE NOT NULL,
                    port INTEGER,
                    credential_id INTEGER REFERENCES credential_profiles(id) ON DELETE RESTRICT
                );
                """;

        var CREATE_METRIC_GROUP_NAMES = """
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'metric_group_name') THEN
                        CREATE TYPE metric_group_name AS ENUM ('CPUINFO', 'CPUUSAGE', 'UPTIME', 'MEMORY', 'DISK', 'PROCESS', 'NETWORK', 'SYSTEMINFO');
                    END IF;
                END
                $$;
                """;

        var CREATE_METRIC_GROUP_TABLE = """
                CREATE TABLE IF NOT EXISTS metric_groups (
                    id SERIAL PRIMARY KEY,
                    provision_profile_id INTEGER REFERENCES provision_profiles(id) ON DELETE CASCADE,
                    name metric_group_name NOT NULL,
                    polling_interval INTEGER NOT NULL,
                    enable BOOLEAN NOT NULL DEFAULT false,
                    UNIQUE (provision_profile_id, name)
                );
                """;

        return PostgresQuery
                .execute(CREATE_PROVISION_PROFILES_TABLE)
                .compose(v -> PostgresQuery.execute(CREATE_METRIC_GROUP_NAMES))
                .compose(v -> PostgresQuery.execute(CREATE_METRIC_GROUP_TABLE))
                .mapEmpty();
    }

    @Override
    public Future<JsonArray> get(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected provision profile ID");
        }

        var GET_PROVISION_BY_ID = """
                SELECT p.id, p.ip, p.port,
                       json_build_object(
                           'id', c.id,
                           'username', c.username,
                           'password', c.password
                       ) AS credentials,
                       json_agg(json_build_object(
                           'id', m.id,
                           'provision_profile_id', m.provision_profile_id,
                           'name', m.name,
                           'polling_interval', m.polling_interval,
                           'enable', m.enable
                       )) AS metric_groups
                FROM provision_profiles p
                LEFT JOIN credential_profiles c ON p.credential_id = c.id
                LEFT JOIN metric_groups m ON p.id = m.provision_profile_id
                WHERE p.id = $1
                GROUP BY p.id, c.id;
                """;

        return PostgresQuery
                .execute(GET_PROVISION_BY_ID, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Provision profile retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve provision profile: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> getAll()
    {
        var GET_ALL_PROVISIONS = """
                SELECT p.id, p.ip, p.port,
                       json_build_object(
                           'id', c.id,
                           'username', c.username,
                           'password', c.password
                       ) AS credentials,
                       json_agg(json_build_object(
                           'id', m.id,
                           'provision_profile_id', m.provision_profile_id,
                           'name', m.name,
                           'polling_interval', m.polling_interval,
                           'enable', m.enable
                       )) AS metric_groups
                FROM provision_profiles p
                LEFT JOIN credential_profiles c ON p.credential_id = c.id
                LEFT JOIN metric_groups m ON p.id = m.provision_profile_id
                GROUP BY p.id, c.id;
                """;

        return PostgresQuery
                .execute(GET_ALL_PROVISIONS)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ All provision profiles retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve all provision profiles: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> save(JsonArray params)
    {
        if (params == null || params.size() != 2)
        {
            return Future.failedFuture("Invalid parameters: Expected discovery_profile_id, ip");
        }

        var CREATE_PROVISION = """
                WITH discovery_validation AS (
                    SELECT dr.ip, dr.credential_id, dp.port
                    FROM discovery_results dr
                    JOIN discovery_profiles dp ON dr.discovery_profile_id = dp.id
                    WHERE dr.discovery_profile_id = $1
                    AND dr.ip = $2
                    AND dr.status = 'COMPLETED'
                ),
                inserted_provision AS (
                    INSERT INTO provision_profiles (ip, port, credential_id)
                    SELECT ip, port, credential_id
                    FROM discovery_validation
                    RETURNING *
                ),
                inserted_metrics AS (
                    INSERT INTO metric_groups (provision_profile_id, name, polling_interval)
                    SELECT p.id, m.metric_group_name, 30
                    FROM inserted_provision p,
                         (SELECT unnest(enum_range(NULL::metric_group_name)) AS metric_group_name) m
                    RETURNING *
                )
                SELECT
                    p.*,
                    json_build_object(
                        'id', c.id,
                        'username', c.username,
                        'password', c.password
                    ) AS credentials,
                    COALESCE(
                        (SELECT json_agg(
                            json_build_object(
                                'id', m.id,
                                'provision_profile_id', m.provision_profile_id,
                                'name', m.name,
                                'polling_interval', m.polling_interval,
                                'enable', m.enable
                            )
                        )
                        FROM inserted_metrics m
                        WHERE m.provision_profile_id = p.id),
                        '[]'::json
                    ) AS metric_groups
                FROM inserted_provision p
                JOIN credential_profiles c ON p.credential_id = c.id;
                """;

        return PostgresQuery
                .execute(CREATE_PROVISION, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Provision profile saved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to save provision profile: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> update(JsonArray params)
    {
        if (params == null || params.size() != 4)
        {
            return Future.failedFuture("Invalid parameters: Expected provision_profile_id, polling_interval, name, enable");
        }

        var UPDATE_METRIC_GROUP = """
                UPDATE metric_groups
                SET polling_interval = COALESCE($2, polling_interval),
                    enable = COALESCE($4, enable)
                WHERE provision_profile_id = $1 AND name = $3 AND EXISTS (
                    SELECT 1 FROM provision_profiles p
                    WHERE p.id = metric_groups.provision_profile_id
                )
                RETURNING *;
                """;

        return PostgresQuery
                .execute(UPDATE_METRIC_GROUP, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Provision profile updated successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to update provision profile: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> delete(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected provision profile ID");
        }

        var DELETE_PROVISION_BY_ID = """
                DELETE FROM provision_profiles
                WHERE id = $1
                RETURNING *;
                """;

        return PostgresQuery
                .execute(DELETE_PROVISION_BY_ID, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Provision profile deleted successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to delete provision profile: " + getMeaningfulErrorMessage(err)));
    }
}