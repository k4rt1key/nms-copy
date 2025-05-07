package org.nms;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.nms.Database.Models.*;
import org.nms.API.Server;
import org.nms.Scheduler.Scheduler;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class App
{
    public static Vertx vertx = Vertx.vertx(new VertxOptions().setMaxWorkerExecuteTime(300).setMaxWorkerExecuteTimeUnit(TimeUnit.SECONDS));

    public static final UserModel userModel = UserModel.getInstance();

    public static final CredentialModel credentialModel = CredentialModel.getInstance();

    public static final DiscoveryModel discoveryModel = DiscoveryModel.getInstance();

    public static final ProvisionModel provisionModel = ProvisionModel.getInstance();

    public static final MetricResultModel metricResultModel = MetricResultModel.getInstance();

    public static Future<Void> createUserSchemaFuture = userModel.createSchema();

    public static Future<Void> createCredentialSchemaFuture = credentialModel.createSchema();

    public static Future<Void> createDiscoverySchemaFuture = discoveryModel.createSchema();

    public static Future<Void> createProvisionSchemaFuture = provisionModel.createSchema();

    public static Future<Void> createPolledDataSchemaFuture = metricResultModel.createSchema();


    public static void main( String[] args )
    {

        Future.join(List.of(
                createUserSchemaFuture,
                createCredentialSchemaFuture,
                createDiscoverySchemaFuture,
                createProvisionSchemaFuture,
                createPolledDataSchemaFuture
        ))
                .compose(v -> vertx.deployVerticle(new Scheduler()))
                .compose(v -> vertx.deployVerticle(new Server()))
                .onSuccess(v -> ConsoleLogger.info("✅ Successfully Started NMS Application"))
                .onFailure(err ->  ConsoleLogger.error("❌ Failed to start NMS Application " + err.getMessage()));
    }
}
