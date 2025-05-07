package org.nms.Database.Models;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Tuple;
import org.nms.ConsoleLogger;
import org.nms.Database.PostgresQuery;

import java.util.List;

public class MetricResultModel implements BaseModel
{
    private MetricResultModel()
    {
        // Private constructor
    }

    private static final MetricResultModel instance = new MetricResultModel();

    public static MetricResultModel getInstance()
    {
        return instance;
    }

    private String getMeaningfulErrorMessage(Throwable err)
    {
        String message = err.getMessage();
        if (message.contains("23505"))
        {
            return "Duplicate entry detected: A metric result with this value already exists.";
        }
        else if (message.contains("23503"))
        {
            return "Foreign key violation: Referenced provision profile does not exist.";
        }
        else
        {
            return "Database error: " + message;
        }
    }

    @Override
    public Future<Void> createSchema()
    {
        var CREATE_POLLING_RESULTS_TABLE = """
                CREATE TABLE IF NOT EXISTS polling_results (
                    id SERIAL PRIMARY KEY,
                    provision_profile_id INTEGER REFERENCES provision_profiles(id) ON DELETE CASCADE,
                    name metric_group_name NOT NULL,
                    value JSONB NOT NULL,
                    time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                );
                """;

        return PostgresQuery
                .execute(CREATE_POLLING_RESULTS_TABLE)
                .mapEmpty();
    }

    @Override
    public Future<JsonArray> get(JsonArray params)
    {
        return Future.failedFuture("Not Implemented");
    }

    @Override
    public Future<JsonArray> getAll()
    {
        var GET_ALL_POLLED_DATA = """
                SELECT * FROM polling_results;
                """;

        return PostgresQuery
                .execute(GET_ALL_POLLED_DATA)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ All metric results retrieved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to retrieve all metric results: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> save(JsonArray params)
    {
        if (params == null || params.size() != 3)
        {
            return Future.failedFuture("Invalid parameters: Expected provision_profile_id, name, value");
        }

        var CREATE_POLLING_RESULTS = """
                INSERT INTO polling_results (provision_profile_id, name, value)
                VALUES ($1, $2, $3)
                RETURNING id;
                """;

        return PostgresQuery
                .execute(CREATE_POLLING_RESULTS, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Metric result saved successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to save metric result: " + getMeaningfulErrorMessage(err)));
    }

    public Future<JsonArray> save(List<Tuple> params)
    {
        if (params == null || params.isEmpty())
        {
            return Future.failedFuture("Invalid parameters: Expected non-empty tuple list");
        }

        var CREATE_POLLING_RESULTS = """
                INSERT INTO polling_results (provision_profile_id, name, value)
                VALUES ($1, $2, $3)
                RETURNING id;
                """;

        return PostgresQuery
                .execute(CREATE_POLLING_RESULTS, params)
                .map(PostgresQuery::toJsonArray)
                .onSuccess(result -> ConsoleLogger.info("✅ Metric results saved in batch successfully"))
                .onFailure(err -> ConsoleLogger.error("❌ Failed to save metric results in batch: " + getMeaningfulErrorMessage(err)));
    }

    @Override
    public Future<JsonArray> update(JsonArray params)
    {
        return Future.failedFuture("Not Implemented");
    }

    @Override
    public Future<JsonArray> delete(JsonArray params)
    {
        return Future.failedFuture("Not Implemented");
    }
}