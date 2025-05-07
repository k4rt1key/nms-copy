package org.nms.Database.Models;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import org.nms.ConsoleLogger;
import org.nms.Database.PostgresQuery;

public class CredentialModel implements BaseModel
{
    private CredentialModel()
    {
        // Private constructor
    }

    private static final CredentialModel instance = new CredentialModel();

    public static CredentialModel getInstance()
    {
        return instance;
    }

    private String getMeaningfulErrorMessage(Throwable err)
    {
        String message = err.getMessage();
        if (message.contains("23505"))
        {
            return "Duplicate entry detected: A credential with this name already exists.";
        }
        else if (message.contains("23503"))
        {
            return "Foreign key violation: Referenced record does not exist.";
        }
        else
        {
            return "Database error: " + message;
        }
    }

    @Override
    public Future<Void> createSchema()
    {
        var CREATE_CREDENTIAL_TYPE = """
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'credential_type') THEN
                        CREATE TYPE credential_type AS ENUM ('WINRM', 'SSH', 'SNMPv1', 'SNMPv2c', 'SNMPv3');
                    END IF;
                END
                $$;
                """;

        var CREATE_CREDENTIAL_PROFILES_TABLE = """
                CREATE TABLE IF NOT EXISTS credential_profiles (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255) UNIQUE NOT NULL,
                    type credential_type NOT NULL,
                    username VARCHAR(255),
                    password VARCHAR(255),
                    community VARCHAR(255),
                    auth_protocol VARCHAR(50),
                    auth_password VARCHAR(255),
                    privacy_protocol VARCHAR(50),
                    privacy_password VARCHAR(255)
                );
                """;

        return PostgresQuery
                .execute(CREATE_CREDENTIAL_TYPE)
                .compose(v -> PostgresQuery.execute(CREATE_CREDENTIAL_PROFILES_TABLE))
                .mapEmpty();
    }

    @Override
    public Future<JsonArray> get(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected credential ID");
        }

        var GET_CREDENTIAL_BY_ID = """
                SELECT id, name, type, username, password
                FROM credential_profiles
                WHERE id = $1;
                """;

        return PostgresQuery
                .execute(GET_CREDENTIAL_BY_ID, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Credential retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve credential: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> getAll()
    {
        var GET_ALL_CREDENTIALS = """
                SELECT id, name, type, username, password
                FROM credential_profiles;
                """;

        return PostgresQuery
                .execute(GET_ALL_CREDENTIALS)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ All credentials retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve all credentials: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> save(JsonArray params)
    {
        if (params == null || params.size() != 9)
        {
            return Future.failedFuture("Invalid parameters: Expected name, type, username, password, community, auth_protocol, auth_password, privacy_protocol, privacy_password");
        }

        var CREATE_CREDENTIAL = """
                INSERT INTO credential_profiles (
                    name,
                    type,
                    username,
                    password,
                    community,
                    auth_protocol,
                    auth_password,
                    privacy_protocol,
                    privacy_password
                ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
                RETURNING id, name, type, username, password;
                """;

        return PostgresQuery
                .execute(CREATE_CREDENTIAL, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Credential saved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to save credential: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> update(JsonArray params)
    {
        if (params == null || params.size() != 5)
        {
            return Future.failedFuture("Invalid parameters: Expected id, name, type, username, password");
        }

        var UPDATE_CREDENTIAL = """
                UPDATE credential_profiles
                SET
                    name = COALESCE($2, name),
                    type = COALESCE($3, type),
                    username = COALESCE($4, username),
                    password = COALESCE($5, password)
                WHERE id = $1
                RETURNING id, name, type, username, password;
                """;

        return PostgresQuery
                .execute(UPDATE_CREDENTIAL, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Credential updated successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to update credential: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> delete(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected credential ID");
        }

        var DELETE_CREDENTIAL = """
                DELETE FROM credential_profiles
                WHERE id = $1
                RETURNING id, name, type, username, password;
                """;

        return PostgresQuery
                .execute(DELETE_CREDENTIAL, params)
                        .map(PostgresQuery::toJsonArray)
                        .onSuccess(result -> ConsoleLogger.info("✅ Credential deleted successfully"))
                        .onFailure(err -> ConsoleLogger.error("❌ Failed to delete credential: " + getMeaningfulErrorMessage(err)));
    }
}