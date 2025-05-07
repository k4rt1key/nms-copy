package org.nms.Database.Models;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Tuple;
import org.nms.ConsoleLogger;
import org.nms.Database.PostgresQuery;

import java.util.List;

public class DiscoveryModel implements BaseModel
{
    private DiscoveryModel()
    {
        // Private constructor
    }

    private static final DiscoveryModel instance = new DiscoveryModel();

    public static DiscoveryModel getInstance()
    {
        return instance;
    }

    private String getMeaningfulErrorMessage(Throwable err)
    {
        String message = err.getMessage();
        if (message.contains("23505"))
        {
            return "Duplicate entry detected: A discovery profile with this name or IP already exists.";
        }
        else if (message.contains("23503"))
        {
            return "Foreign key violation: Referenced credential or discovery profile does not exist.";
        }
        else
        {
            return "Database error: " + message;
        }
    }

    @Override
    public Future<Void> createSchema()
    {
        var CREATE_IP_TYPE = """
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ip_type') THEN
                        CREATE TYPE ip_type AS ENUM ('SINGLE', 'RANGE', 'SUBNET');
                    END IF;
                END
                $$;
                """;

        var CREATE_DISCOVERY_RESULT_STATUS = """
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'discovery_result_status') THEN
                        CREATE TYPE discovery_result_status AS ENUM ('COMPLETED', 'FAIL');
                    END IF;
                END
                $$;
                """;

        var CREATE_DISCOVERY_STATUS = """
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'discovery_status') THEN
                        CREATE TYPE discovery_status AS ENUM ('PENDING', 'COMPLETED');
                    END IF;
                END
                $$;
                """;

        var CREATE_DISCOVERY_PROFILES_TABLE = """
                CREATE TABLE IF NOT EXISTS discovery_profiles (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255) UNIQUE NOT NULL,
                    ip VARCHAR(255) NOT NULL,
                    ip_type ip_type NOT NULL,
                    status discovery_status DEFAULT 'PENDING',
                    port INTEGER
                );
                """;

        var CREATE_DISCOVERY_CREDENTIALS_TABLE = """
                CREATE TABLE IF NOT EXISTS discovery_credentials (
                    discovery_profile_id INTEGER REFERENCES discovery_profiles(id) ON DELETE CASCADE,
                    credential_id INTEGER REFERENCES credential_profiles(id) ON DELETE CASCADE,
                    PRIMARY KEY (discovery_profile_id, credential_id)
                );
                """;

        var CREATE_DISCOVERY_RESULTS_TABLE = """
                CREATE TABLE IF NOT EXISTS discovery_results (
                    discovery_profile_id INTEGER REFERENCES discovery_profiles(id) ON DELETE CASCADE,
                    ip VARCHAR(255) NOT NULL,
                    credential_id INTEGER REFERENCES credential_profiles(id) ON DELETE RESTRICT,
                    message TEXT,
                    status discovery_result_status NOT NULL,
                    PRIMARY KEY (discovery_profile_id, ip),
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                );
                """;

        return PostgresQuery
                .execute(CREATE_IP_TYPE)
                .compose(v -> PostgresQuery.execute(CREATE_DISCOVERY_RESULT_STATUS))
                .compose(v -> PostgresQuery.execute(CREATE_DISCOVERY_STATUS))
                .compose(v -> PostgresQuery.execute(CREATE_DISCOVERY_PROFILES_TABLE))
                .compose(v -> PostgresQuery.execute(CREATE_DISCOVERY_CREDENTIALS_TABLE))
                .compose(v -> PostgresQuery.execute(CREATE_DISCOVERY_RESULTS_TABLE))
                .mapEmpty();
    }

    @Override
    public Future<JsonArray> get(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected discovery profile ID");
        }

        var GET_DISCOVERY_PROFILE_BY_ID = """
                SELECT * FROM discovery_profiles
                WHERE id = $1;
                """;

        return PostgresQuery
                .execute(GET_DISCOVERY_PROFILE_BY_ID, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery profile retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve discovery profile: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> getWithCredentialsById(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected discovery profile ID");
        }

        var GET_DISCOVERY_PROFILES_WITH_CREDENTIALS = """
                SELECT
                    dp.id AS id,
                    dp.name AS name,
                    dp.ip AS ip,
                    dp.ip_type AS ip_type,
                    dp.status AS status,
                    dp.port AS port,
                    ARRAY_AGG(
                        JSON_BUILD_OBJECT(
                            'id', cp.id,
                            'name', cp.name,
                            'username', cp.username,
                            'password', cp.password
                        )
                    ) AS credentials
                FROM discovery_profiles dp
                LEFT JOIN discovery_credentials dc ON dp.id = dc.discovery_profile_id
                LEFT JOIN credential_profiles cp ON dc.credential_id = cp.id
                WHERE dp.id = $1
                GROUP BY dp.id, dp.name, dp.ip, dp.ip_type, dp.status, dp.port;
                """;

        return PostgresQuery
                .execute(GET_DISCOVERY_PROFILES_WITH_CREDENTIALS, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery profile with credentials retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve discovery profile with credentials: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> getWithResultsById(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected discovery profile ID");
        }

        var GET_DISCOVERY_WITH_RESULTS = """
                SELECT
                    dp.id AS id,
                    dp.name AS name,
                    dp.ip AS ip,
                    dp.ip_type AS ip_type,
                    dp.status AS status,
                    dp.port AS port,
                    COALESCE(
                        json_agg(
                            json_build_object(
                                'ip', dr.ip,
                                'windows_credential', json_build_object(
                                    'id', cp.id,
                                    'name', cp.name,
                                    'type', cp.type,
                                    'username', cp.username,
                                    'password', cp.password
                                ),
                                'message', dr.message,
                                'status', dr.status,
                                'created_at', dr.created_at
                            )
                        ) FILTER (WHERE dr.ip IS NOT NULL),
                        '[]'
                    ) AS results
                FROM discovery_profiles dp
                LEFT JOIN discovery_results dr ON dr.discovery_profile_id = dp.id
                LEFT JOIN credential_profiles cp ON cp.id = dr.credential_id
                WHERE dp.id = $1
                GROUP BY dp.id, dp.name, dp.ip, dp.ip_type, dp.status, dp.port
                ORDER BY dp.id;
                """;

        return PostgresQuery
                .execute(GET_DISCOVERY_WITH_RESULTS, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery profile with results retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve discovery profile with results: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> getAll()
    {
        var GET_ALL_DISCOVERY_PROFILES = """
                SELECT * FROM discovery_profiles;
                """;

        return PostgresQuery
                .execute(GET_ALL_DISCOVERY_PROFILES)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ All discovery profiles retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve all discovery profiles: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> getAllWithCredentials()
    {
        var GET_ALL_DISCOVERY_PROFILES_WITH_CREDENTIALS = """
                SELECT
                    dp.id AS id,
                    dp.name AS name,
                    dp.ip AS ip,
                    dp.ip_type AS id_type,
                    dp.status AS status,
                    dp.port AS port,
                    ARRAY_AGG(
                        JSON_BUILD_OBJECT(
                            'id', cp.id,
                            'name', cp.name,
                            'username', cp.username,
                            'password', cp.password
                        )
                    ) AS credentials
                FROM discovery_profiles dp
                LEFT JOIN discovery_credentials dc ON dp.id = dc.discovery_profile_id
                LEFT JOIN credential_profiles cp ON dc.credential_id = cp.id
                GROUP BY dp.id, dp.name, dp.ip, dp.ip_type, dp.status, dp.port;
                """;

        return PostgresQuery
                .execute(GET_ALL_DISCOVERY_PROFILES_WITH_CREDENTIALS)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ All discovery profiles with credentials retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve all discovery profiles with credentials: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> getAllWithResults()
    {
        var GET_ALL_DISCOVERIES_WITH_RESULTS = """
                SELECT
                    dp.id AS discovery_profile_id,
                    dp.name AS discovery_profile_name,
                    dp.ip,
                    dp.ip_type,
                    dp.status AS discovery_profile_status,
                    dp.port,
                    COALESCE(
                        json_agg(
                            json_build_object(
                                'ip', dr.ip,
                                'windows_credential', json_build_object(
                                    'id', cp.id,
                                    'name', cp.name,
                                    'type', cp.type,
                                    'username', cp.username,
                                    'password', cp.password
                                ),
                                'message', dr.message,
                                'status', dr.status,
                                'created_at', dr.created_at
                            )
                        ) FILTER (WHERE dr.ip IS NOT NULL),
                        '[]'
                    ) AS results
                FROM discovery_profiles dp
                LEFT JOIN discovery_results dr ON dr.discovery_profile_id = dp.id
                LEFT JOIN credential_profiles cp ON cp.id = dr.credential_id
                GROUP BY dp.id, dp.name, dp.ip, dp.ip_type, dp.status, dp.port
                ORDER BY dp.id;
                """;

        return PostgresQuery
                .execute(GET_ALL_DISCOVERIES_WITH_RESULTS)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ All discovery profiles with results retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve all discovery profiles with results: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> save(JsonArray params)
    {
        if (params == null || params.size() != 4)
        {
            return Future.failedFuture("Invalid parameters: Expected name, ip, ip_type, port");
        }

        var CREATE_DISCOVERY_PROFILE = """
                INSERT INTO discovery_profiles (
                    name,
                    ip,
                    ip_type,
                    status,
                    port
                ) VALUES ($1, $2, $3, 'PENDING', $4)
                RETURNING id, name, ip, ip_type, status, port;
                """;

        return PostgresQuery
                .execute(CREATE_DISCOVERY_PROFILE, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery profile saved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to save discovery profile: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> saveResults(List<Tuple> params)
    {
        if (params == null || params.isEmpty())
        {
            return Future.failedFuture("Invalid parameters: Expected non-empty tuple list");
        }

        var CREATE_DISCOVERY_RESULTS = """
                INSERT INTO discovery_results (
                    discovery_profile_id,
                    ip,
                    credential_id,
                    message,
                    status
                ) VALUES ($1, $2, $3, $4, $5)
                ON CONFLICT (discovery_profile_id, ip) DO NOTHING
                RETURNING *;
                """;

        return PostgresQuery
                .execute(CREATE_DISCOVERY_RESULTS, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery results saved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to save discovery results: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> saveCredentials(List<Tuple> params)
    {
        if (params == null || params.isEmpty())
        {
            return Future.failedFuture("Invalid parameters: Expected non-empty tuple list");
        }

        var CREATE_DISCOVERY_CREDENTIALS = """
                INSERT INTO discovery_credentials (discovery_profile_id, credential_id)
                VALUES ($1, $2)
                ON CONFLICT (discovery_profile_id, credential_id) DO NOTHING
                RETURNING discovery_profile_id, credential_id;
                """;

        return PostgresQuery
                .execute(CREATE_DISCOVERY_CREDENTIALS, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery credentials saved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to save discovery credentials: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> update(JsonArray params)
    {
        if (params == null || params.size() != 5)
        {
            return Future.failedFuture("Invalid parameters: Expected id, name, ip, ip_type, port");
        }

        var UPDATE_DISCOVERY_PROFILE = """
                UPDATE discovery_profiles
                SET
                    name = COALESCE($2, name),
                    ip = COALESCE($3, ip),
                    ip_type = COALESCE($4, ip_type),
                    port = COALESCE($5, port)
                WHERE id = $1 AND status = 'PENDING'
                RETURNING id, name, ip, ip_type, port, status;
                """;

        return PostgresQuery
                .execute(UPDATE_DISCOVERY_PROFILE, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery profile updated successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to update discovery profile: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> updateStatus(JsonArray params)
    {
        if (params == null || params.size() != 2)
        {
            return Future.failedFuture("Invalid parameters: Expected id, status");
        }

        var UPDATE_DISCOVERY_PROFILE_STATUS = """
                UPDATE discovery_profiles
                SET status = $2
                WHERE id = $1
                RETURNING *;
                """;

        return PostgresQuery
                .execute(UPDATE_DISCOVERY_PROFILE_STATUS, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery profile status updated successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to update discovery profile status: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> delete(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected discovery profile ID");
        }

        var DELETE_DISCOVERY_PROFILE = """
                DELETE FROM discovery_profiles
                WHERE id = $1
                RETURNING id, name, ip, ip_type;
                """;

        return PostgresQuery
                .execute(DELETE_DISCOVERY_PROFILE, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery profile deleted successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to delete discovery profile: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> deleteCredentials(List<Tuple> params)
    {
        if (params == null || params.isEmpty())
        {
            return Future.failedFuture("Invalid parameters: Expected non-empty tuple list");
        }

        var DELETE_DISCOVERY_CREDENTIALS = """
                DELETE FROM discovery_credentials dc
                USING discovery_profiles dp
                WHERE dc.discovery_profile_id = dp.id
                  AND dp.status = 'PENDING'
                  AND dc.discovery_profile_id = $1
                  AND dc.credential_id = $2
                RETURNING dc.discovery_profile_id, dc.credential_id;
                """;

        return PostgresQuery
                .execute(DELETE_DISCOVERY_CREDENTIALS, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Discovery credentials deleted successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to delete discovery credentials: " + getMeaningfulErrorMessage(err)));
    }
}