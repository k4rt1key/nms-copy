package org.nms.Database.Models;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import org.nms.ConsoleLogger;
import org.nms.Database.PostgresQuery;

public class UserModel implements BaseModel
{
    private UserModel()
    {
        // Private constructor
    }

    private static final UserModel instance = new UserModel();

    public static UserModel getInstance()
    {
        return instance;
    }

    private String getMeaningfulErrorMessage(Throwable err)
    {
        String message = err.getMessage();
        if (message.contains("23505"))
        {
            return "Duplicate entry detected: A user with this name already exists.";
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
        var CREATE_USERS_TABLE = """
                CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL
                );
                """;

        return PostgresQuery
                .execute(CREATE_USERS_TABLE)
                .mapEmpty();
    }

    @Override
    public Future<JsonArray> get(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected user ID");
        }

        var GET_USER_BY_ID_QUERY = """
                SELECT * FROM users
                WHERE id = $1;
                """;

        return PostgresQuery
                .execute(GET_USER_BY_ID_QUERY, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ User retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve user by ID: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> getByName(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected username");
        }

        var GET_USER_BY_NAME_QUERY = """
                SELECT * FROM users
                WHERE name = $1;
                """;

        return PostgresQuery
                .execute(GET_USER_BY_NAME_QUERY, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ User retrieved by name successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve user by name: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> getAll()
    {
        var GET_ALL_USERS_QUERY = """
                SELECT * FROM users;
                """;

        return PostgresQuery
                .execute(GET_ALL_USERS_QUERY)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ All users retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve all users: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> save(JsonArray params)
    {
        if (params == null || params.size() != 2)
        {
            return Future.failedFuture("Invalid parameters: Expected name, password");
        }

        var CREATE_USER_QUERY = """
                INSERT INTO users (name, password)
                VALUES ($1, $2)
                RETURNING *;
                """;

        return PostgresQuery
                .execute(CREATE_USER_QUERY, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ User saved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to save user: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> update(JsonArray params)
    {
        if (params == null || params.size() != 4)
        {
            return Future.failedFuture("Invalid parameters: Expected id, name, password");
        }

        var UPDATE_USER_BY_ID = """
                UPDATE users
                SET
                    name = COALESCE($2, name),
                    password = COALESCE($3, password)
                WHERE id = $1
                RETURNING *;
                """;

        return PostgresQuery
                .execute(UPDATE_USER_BY_ID, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ User updated successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to update user: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> delete(JsonArray params)
    {
        if (params == null || params.size() != 1)
        {
            return Future.failedFuture("Invalid parameters: Expected user ID");
        }

        var DELETE_USER_BY_ID = """
                DELETE FROM users
                WHERE id = $1
                RETURNING *;
                """;

        return PostgresQuery
                .execute(DELETE_USER_BY_ID, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ User deleted successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to delete user: " + getMeaningfulErrorMessage(err)));
    }
}