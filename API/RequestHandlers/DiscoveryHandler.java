package org.nms.API.RequestHandlers;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import org.nms.App;
import org.nms.ConsoleLogger;
import org.nms.API.Utility.HttpResponse;
import org.nms.API.Utility.IpHelpers;
import org.nms.PluginManager.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DiscoveryHandler
{
    public static void getAllDiscoveries(RoutingContext ctx)
    {
        App.discoveryModel
                .getAllWithCredentials()
                .onSuccess(discoveries ->
                {
                    // Discoveries not found
                    if (discoveries.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "No discoveries found");
                        return;
                    }

                    // Discoveries found
                    HttpResponse.sendSuccess(ctx, 200, "Discoveries found", discoveries);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void getDiscoveryById(RoutingContext ctx)
    {
        int id = Integer.parseInt(ctx.request().getParam("id"));

        App.discoveryModel
                .getWithCredentialsById(new JsonArray().add(id))
                .onSuccess(discovery ->
                {
                    // Discovery not found
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    // Discovery found
                    HttpResponse.sendSuccess(ctx, 200, "Discovery found", discovery);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void getDiscoveryResultsById(RoutingContext ctx)
    {
        int id = Integer.parseInt(ctx.request().getParam("id"));

        App.discoveryModel
                .getWithResultsById(new JsonArray().add(id))
                .onSuccess(discovery ->
                {
                    // Discovery not found
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    // Discovery found
                    HttpResponse.sendSuccess(ctx, 200, "Discovery found", discovery);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void getDiscoveryResults(RoutingContext ctx)
    {
        App.discoveryModel
                .getAllWithResults()
                .onSuccess(discoveries ->
                {
                    // Discoveries not found
                    if (discoveries.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "No discoveries found");
                        return;
                    }

                    // Discoveries found
                    HttpResponse.sendSuccess(ctx, 200, "Discoveries found", discoveries);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void createDiscovery(RoutingContext ctx)
    {
        var name = ctx.body().asJsonObject().getString("name");

        var ip = ctx.body().asJsonObject().getString("ip");

        var ipType = ctx.body().asJsonObject().getString("ip_type");

        var credentials = ctx.body().asJsonObject().getJsonArray("credentials");

        var port = ctx.body().asJsonObject().getInteger("port");

        // Step 1: Create discovery
        App.discoveryModel
                .save(new JsonArray().add(name).add(ip).add(ipType).add(port))
                .onSuccess(discovery ->
                {
                    // !!!! Discovery Not Created
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 400, "Failed to create discovery");
                        return;
                    }

                    // Step 2: Add credentials to discovery
                    List<Tuple> credentialsToAdd = new ArrayList<>();

                    var discoveryId = discovery.getJsonObject(0).getInteger("id");

                    for (int i = 0; i < credentials.size(); i++)
                    {
                        var credentialId = Integer.parseInt(credentials.getString(i));

                        credentialsToAdd.add(Tuple.of(discoveryId, credentialId));
                    }

                    // Save Credentials Into DB
                    App.discoveryModel
                            .saveCredentials(credentialsToAdd)
                            .onSuccess(discoveryCredentials ->
                            {
                                // !!! Credentials Not Created
                                if (discoveryCredentials.isEmpty())
                                {
                                    HttpResponse.sendFailure(ctx, 400, "Failed to add credentials to discovery");
                                    return;
                                }

                                // Step 3: Get complete discovery with credentials
                                App.discoveryModel
                                        .getWithCredentialsById(new JsonArray().add(discoveryId))
                                        .onSuccess(discoveryWithCredentials -> HttpResponse.sendSuccess(ctx, 201, "Discovery created successfully", discoveryWithCredentials))
                                        .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                            })
                            .onFailure(err ->
                            {
                                // Rollback discovery creation if adding credentials fails
                                App.discoveryModel
                                        .delete(new JsonArray().add(discoveryId))
                                        .onFailure(rollbackErr -> ConsoleLogger.error("Failed to rollback discovery creation: " + rollbackErr.getMessage()));

                                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage());
                            });
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void updateDiscovery(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if discovery exists
        App.discoveryModel
                .getWithCredentialsById(new JsonArray().add(id))
                .onSuccess(discovery ->
                {
                    // Step-1: Check if discovery Exist
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    // Discovery Already run
                    if(discovery.getJsonObject(0).getString("status").equals("COMPLETE"))
                    {
                        HttpResponse.sendFailure(ctx, 400, "Discovery Already Run");
                        return;
                    }

                    // Discovery found, proceed with update
                    var name = ctx.body().asJsonObject().getString("name");

                    var ip = ctx.body().asJsonObject().getString("ip");

                    var ipType = ctx.body().asJsonObject().getString("ip_type");

                    var port = ctx.body().asJsonObject().getInteger("port");

                    // Update Discovery
                    App.discoveryModel
                            .update(new JsonArray().add(id).add(name).add(ip).add(ipType).add(port))
                            .onSuccess(updatedDiscovery ->
                            {
                                // !!! Discovery Not Updated
                                if (updatedDiscovery.isEmpty())
                                {
                                    HttpResponse.sendFailure(ctx, 400, "Failed to update discovery");
                                    return;
                                }

                                // Get updated discovery with credentials
                                App.discoveryModel
                                        .getWithCredentialsById(new JsonArray().add(id))
                                        .onSuccess(discoveryWithCredentials -> HttpResponse.sendSuccess(ctx, 200, "Discovery updated successfully", discoveryWithCredentials))
                                        .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                            })
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void updateDiscoveryCredentials(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // Step-1: Check if discovery exists
        App.discoveryModel
                .getWithCredentialsById(new JsonArray().add(id))
                .onSuccess(discovery ->
                {
                    // !!! Discovery Not Found
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    var addCredentials = ctx.body().asJsonObject().getJsonArray("add_credentials");

                    var removeCredentials = ctx.body().asJsonObject().getJsonArray("remove_credentials");

                    // Step 2: Add credentials to discovery if any
                    List<Tuple> credentialsToAdd = new ArrayList<>();

                    if (addCredentials != null && !addCredentials.isEmpty()) {
                        for (int i = 0; i < addCredentials.size(); i++)
                        {
                            var credentialId = Integer.parseInt(addCredentials.getString(i));
                            credentialsToAdd.add(Tuple.of(id, credentialId));
                        }

                        App.discoveryModel
                                .saveCredentials(credentialsToAdd)
                                .onSuccess(addedCredentials ->
                                {
                                    // Step 2: Remove credentials if any
                                    processRemoveCredentials(ctx, id, removeCredentials);
                                })
                                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                    }
                    else
                    {
                        // If no credentials to add, proceed to remove credentials
                        processRemoveCredentials(ctx, id, removeCredentials);
                    }
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    // Helper method to process removing credentials
    private static void processRemoveCredentials(RoutingContext ctx, int discoveryId, JsonArray removeCredentials)
    {
        if (removeCredentials != null && !removeCredentials.isEmpty())
        {
            List<Tuple> credentialsToRemove = new ArrayList<>();

            for (int i = 0; i < removeCredentials.size(); i++)
            {
                var credentialId = Integer.parseInt(removeCredentials.getString(i));
                credentialsToRemove.add(Tuple.of(discoveryId, credentialId));
            }

            App.discoveryModel
                    .deleteCredentials(credentialsToRemove)
                    .onSuccess(v -> {
                        // Return updated discovery with credentials
                        returnUpdatedDiscovery(ctx, discoveryId);
                    })
                    .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
        }
        else
        {
            // If no credentials to remove, just return the updated discovery
            returnUpdatedDiscovery(ctx, discoveryId);
        }
    }

    // Helper method to return updated discovery
    private static void returnUpdatedDiscovery(RoutingContext ctx, int discoveryId)
    {
        App.discoveryModel
                .getWithCredentialsById(new JsonArray().add(discoveryId))
                .onSuccess(updatedDiscovery -> {
                    HttpResponse.sendSuccess(ctx, 200, "Discovery credentials updated successfully", updatedDiscovery);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void deleteDiscovery(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if discovery exists
        App.discoveryModel
                .getWithCredentialsById(new JsonArray().add(id))
                .onSuccess(discovery -> {
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    // Discovery found, proceed with delete
                    App.discoveryModel
                            .delete(new JsonArray().add(id))
                            .onSuccess(deletedDiscovery -> HttpResponse.sendSuccess(ctx, 200, "Discovery deleted successfully", discovery))
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    /**
     * Runs a discovery process for a specific ID
     * 1. Validates discovery exists and is in correct state
     * 2. Performs ping checks on IPs
     * 3. Verifies port accessibility
     * 4. Tests credentials
     * 5. Updates status and returns results
     */
    public static void runDiscovery(RoutingContext ctx) {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // Main discovery workflow with proper chaining
        App.discoveryModel.getWithCredentialsById(new JsonArray().add(id))
                .compose(discoveryWithCredentials -> validateDiscovery(ctx, discoveryWithCredentials))
                .compose(DiscoveryHandler::performPingChecks)
                .compose(DiscoveryHandler::performPortChecks)
                .compose(DiscoveryHandler::performCredentialChecks)
                .compose(ignored -> App.discoveryModel.updateStatus(new JsonArray().add(id).add("COMPLETED")))
                .compose(ignored -> App.discoveryModel.getWithResultsById(new JsonArray().add(id)))
                .onSuccess(finalResults -> HttpResponse.sendSuccess(ctx, 200, "Discovery run successfully", finalResults))
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    /**
     * Validates that the discovery exists and is in a valid state
     */
    private static Future<JsonObject> validateDiscovery(RoutingContext ctx, JsonArray discoveryWithCredentials) {
        if (discoveryWithCredentials.isEmpty()) {
            HttpResponse.sendFailure(ctx, 404, "Discovery not found");
            return Future.failedFuture(new Exception("Discovery not found"));
        }

        var discovery = discoveryWithCredentials.getJsonObject(0);
        var status = discovery.getString("status");

        if (status.equals("COMPLETED")) {
            HttpResponse.sendFailure(ctx, 400, "Discovery is already complete");
            return Future.failedFuture(new Exception("Discovery is already complete"));
        }

        // Return discovery data for next step
        return Future.succeededFuture(discovery);
    }

    /**
     * Performs ping checks on the IPs in the discovery
     */
    private static Future<JsonObject> performPingChecks(JsonObject discovery)
    {
        var ips = discovery.getString("ip");
        var ipType = discovery.getString("ip_type");
        var port = discovery.getInteger("port");
        var id = discovery.getInteger("id");
        var ipArray = IpHelpers.getIpListAsJsonArray(ips, ipType);
        var credentials = discovery.getJsonArray("credentials");

        // Execute ping check in blocking context
        return executeBlocking(vertx -> IpHelpers.pingIps(ipArray))
                .compose(pingResults -> processPingResults(id, pingResults))
                .map(passedIps -> new JsonObject()
                        .put("id", id)
                        .put("port", port)
                        .put("passedIps", passedIps)
                        .put("credentials", credentials));
    }

    /**
     * Process ping results and save failed IPs to the database
     */
    private static Future<JsonArray> processPingResults(Integer id, JsonArray pingResults)
    {
        var passedIps = new JsonArray();
        var failedIps = new JsonArray();

        // Separate successful and failed pings
        for (int i = 0; i < pingResults.size(); i++)
        {
            var result = pingResults.getJsonObject(i);

            if (result.getBoolean("success"))
            {
                passedIps.add(result.getString("ip"));
            }
            else
            {
                failedIps.add(result);
            }
        }

        // Save failed ping results if any
        if (!failedIps.isEmpty())
        {
            List<Tuple> ipsToAdd = new ArrayList<>();

            for (var i = 0; i < failedIps.size(); i++)
            {
                var ip = failedIps.getJsonObject(i).getString("ip");
                var message = failedIps.getJsonObject(i).getString("message");
                ipsToAdd.add(Tuple.of(id, ip, null, message, "FAIL"));
            }

            return App.discoveryModel.saveResults(ipsToAdd)
                    .map(ignored -> passedIps);
        }

        return Future.succeededFuture(passedIps);
    }

    /**
     * Performs port checks on IPs that passed ping checks
     */
    private static Future<JsonObject> performPortChecks(JsonObject data)
    {
        var id = data.getInteger("id");
        var port = data.getInteger("port");
        var passedIps = data.getJsonArray("passedIps");
        var credentials = data.getJsonArray("credentials");

        // Create futures for each port check
        List<Future<JsonObject>> portCheckFutures = new ArrayList<>();
        for (int i = 0; i < passedIps.size(); i++)
        {
            String ip = passedIps.getString(i);
            portCheckFutures.add(
                    executeBlocking(vertx -> IpHelpers.checkPort(ip, port))
                            .otherwise(err -> new JsonObject()
                                    .put("ip", ip)
                                    .put("message", err.getMessage())
                                    .put("success", false))
            );
        }

        // Wait for all port checks to complete
        return Future.all(portCheckFutures)
                .compose(compositeFuture ->
                {
                    JsonArray portCheckResults = new JsonArray();

                    for (int i = 0; i < compositeFuture.size(); i++)
                    {
                        JsonObject result = compositeFuture.resultAt(i);
                        if (result != null)
                        {
                            portCheckResults.add(result);
                        }
                    }
                    return processPortResults(id, portCheckResults);
                })
                .map(passedPortIps -> new JsonObject()
                        .put("id", id)
                        .put("port", port)
                        .put("passedIps", passedPortIps)
                        .put("credentials", credentials));
    }

    /**
     * Process port check results and save failed ports to the database
     */
    private static Future<JsonArray> processPortResults(Integer id, JsonArray portCheckResults) {
        var passedPortIps = new JsonArray();
        var failedPortIps = new JsonArray();

        // Separate successful and failed port checks
        for (int i = 0; i < portCheckResults.size(); i++)
        {
            var result = portCheckResults.getJsonObject(i);

            if (result.getBoolean("success"))
            {
                passedPortIps.add(result.getString("ip"));
            }
            else
            {
                failedPortIps.add(result);
            }
        }

        // Save failed port check results if any
        if (!failedPortIps.isEmpty())
        {
            List<Tuple> portCheckIpsToAdd = new ArrayList<>();

            for (var i = 0; i < failedPortIps.size(); i++)
            {
                var ip = failedPortIps.getJsonObject(i).getString("ip");
                var message = failedPortIps.getJsonObject(i).getString("message");
                portCheckIpsToAdd.add(Tuple.of(id, ip, null, message, "FAIL"));
            }

            return App.discoveryModel.saveResults(portCheckIpsToAdd)
                    .map(ignored -> passedPortIps);
        }

        return Future.succeededFuture(passedPortIps);
    }

    /**
     * Performs credential checks on IPs that passed port checks
     */
    private static Future<Void> performCredentialChecks(JsonObject data)
    {
        ConsoleLogger.info("Starting credential checks");
        var id = data.getInteger("id");
        var port = data.getInteger("port");
        var passedIps = data.getJsonArray("passedIps");
        var credentials = data.getJsonArray("credentials");

        // Run credential checks through plugin manager
        return PluginManager.runDiscovery(id, passedIps, port, credentials)
                .compose(credentialResults -> processCredentialResults(id, credentialResults));
    }

    /**
     * Process credential check results and save them to the database
     */
    private static Future<Void> processCredentialResults(Integer id, JsonArray credentialResults)
    {
        var successResults = new JsonArray();
        var failedResults = new JsonArray();

        // Separate successful and failed credential checks
        for (int i = 0; i < credentialResults.size(); i++)
        {
            var result = credentialResults.getJsonObject(i);
            if (result.getBoolean("success"))
            {
                successResults.add(result);
            }
            else
            {
                failedResults.add(result);
            }
        }

        // Prepare failed credential results
        List<Tuple> failedEntries = new ArrayList<>();

        for (int i = 0; i < failedResults.size(); i++)
        {
            var result = failedResults.getJsonObject(i);
            var ip = result.getString("ip");
            var message = result.getString("message");
            failedEntries.add(Tuple.of(id, ip, null, message, "FAIL"));
        }

        // Prepare successful credential results
        List<Tuple> successEntries = new ArrayList<>();
        for (int i = 0; i < successResults.size(); i++)
        {
            var result = successResults.getJsonObject(i);
            var ip = result.getString("ip");
            var message = result.getString("message");
            var credentialId = result.getJsonObject("credential").getInteger("id");
            successEntries.add(Tuple.of(id, ip, credentialId, message, "COMPLETED"));
        }

        // Save results - handle empty lists gracefully
        Future<JsonArray> failuresFuture = failedEntries.isEmpty()
                ? Future.succeededFuture(new JsonArray())
                : App.discoveryModel.saveResults(failedEntries);

        Future<JsonArray> successFuture = successEntries.isEmpty()
                ? Future.succeededFuture(new JsonArray())
                : App.discoveryModel.saveResults(successEntries);

        return Future.join(failuresFuture, successFuture).mapEmpty();
    }

    /**
     * Helper method to execute blocking operations
     */
    private static <T> Future<T> executeBlocking(Function<Vertx, T> operation)
    {
        return Future.future(promise ->
                App.vertx.executeBlocking(
                        () -> {
                            try
                            {
                               operation.apply(App.vertx);
                            }
                            catch (Exception ignored)
                            {
                                // Ignored
                            }

                            return null;
                        },
                        false,
                        ar -> {
                            if (ar.succeeded()) {
                                promise.complete((T) ar.result());
                            } else {
                                promise.fail(ar.cause());
                            }
                        }
                )
        );
    }
}