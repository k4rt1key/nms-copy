package org.nms.Database;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.nms.ConsoleLogger;

import java.util.List;

public class PostgresQuery
{

    public static Future<RowSet<Row>> execute(String sql, JsonArray params)
    {
        if(PostgresClient.client != null)
        {
            return PostgresClient.client.preparedQuery(sql).execute(Tuple.wrap(params.getList().toArray()));
        }
        else
        {
            ConsoleLogger.error("❌ Postgres Client Is Null");

            return Future.failedFuture("❌ Postgres Client Is Null");
        }
    }

    public static Future<RowSet<Row>> execute(String sql)
    {
        if(PostgresClient.client != null)
        {
            return PostgresClient.client.preparedQuery(sql).execute();
        }
        else
        {
            ConsoleLogger.error("❌ Postgres Client Is Null");

            return Future.failedFuture("❌ Postgres Client Is Null");
        }
    }

    public static Future<RowSet<Row>> execute(String sql, List<Tuple> params)
    {
        if(PostgresClient.client != null)
        {
            return PostgresClient.client.preparedQuery(sql).executeBatch(params);
        }
        else
        {
            ConsoleLogger.error("❌ Postgres Client Is Null");

            return Future.failedFuture("❌ Postgres Client Is Null");
        }

    }

    public static JsonArray toJsonArray(RowSet<Row> rows)
    {
        JsonArray jsonArray = new JsonArray();

        for (Row row : rows)
        {
            jsonArray.add(row.toJson());
        }

        return jsonArray;
    }
}
