package org.nms.Database;

import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import org.nms.App;
import org.nms.ConsoleLogger;

public class PostgresClient
{
    private static final String DB_URL = "localhost";
    private static final int DB_PORT = 5000;
    private static final String DB_NAME = "nms";
    private static final String DB_USER = "nms";
    private static final String DB_PASSWORD = "nms";

    public static final SqlClient client = createPostgresClient();

    private static SqlClient createPostgresClient()
    {
        try {
            var connectOptions = new PgConnectOptions()
                    .setPort(DB_PORT)
                    .setHost(DB_URL)
                    .setDatabase(DB_NAME)
                    .setUser(DB_USER)
                    .setPassword(DB_PASSWORD);

            var poolOptions = new PoolOptions().setMaxSize(5);

            return PgBuilder
                    .client()
                    .with(poolOptions)
                    .connectingTo(connectOptions)
                    .using(App.vertx)
                    .build();
        }
        catch (Exception e)
        {
            ConsoleLogger.error("Failed to create Postgres client");

            return null;
        }
    }
}
