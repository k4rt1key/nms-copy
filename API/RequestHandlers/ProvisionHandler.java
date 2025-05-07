package org.nms.API.RequestHandlers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.nms.App;
import org.nms.Cache.MetricGroupCacheStore;
import org.nms.ConsoleLogger;
import org.nms.API.Utility.HttpResponse;

import java.util.ArrayList;
import java.util.List;

public class ProvisionHandler
{
    public static void getAllProvisions(RoutingContext ctx)
    {
        App.provisionModel
                .getAll()
                .onSuccess(provisions ->
                {
                    // Provisions not found
                    if (provisions.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "No provisions found");
                        return;
                    }

                    // Provisions found
                    HttpResponse.sendSuccess(ctx, 200, "Provisions found", provisions);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));

    }

    public static void getProvisionById(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        App.provisionModel
                .get(new JsonArray().add(id))
                .onSuccess(provision ->
                {
                    // Provision not found
                    if (provision.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Provision not found");
                        return;
                    }

                    // Provision found
                    HttpResponse.sendSuccess(ctx, 200, "Provision found", provision);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));

    }

    public static void createProvision(RoutingContext ctx)
    {
        var discovery_id = Integer.parseInt(ctx.body().asJsonObject().getString("discovery_id"));
        var ips = ctx.body().asJsonObject().getJsonArray("ips");
        App.discoveryModel
                .getWithResultsById(new JsonArray().add(discovery_id))
                .onSuccess(discoveriesWithResult ->
                {
                    if(discoveriesWithResult == null || discoveriesWithResult.isEmpty() )
                    {
                        HttpResponse.sendFailure(ctx, 404, "Failed To Get Discovery with that id");
                        return;
                    }

                    if(discoveriesWithResult.getJsonObject(0).getString("status").equals("PENDING"))
                    {
                        HttpResponse.sendFailure(ctx, 409, "Can't provision pending discovery");
                        return;
                    }
                    var results = discoveriesWithResult.getJsonObject(0).getJsonArray("results");

                    // Check if user sent wrong Ips
                    var wrongIps = new ArrayList<String>();


                    List<Future> provisionsToAddFutures = new ArrayList<>();

                    if(results != null && results.size() > 0)
                    {
                        for(int j = 0; j < ips.size(); j++){

                            var isAnyWrongIp = true;
                            for(int i = 0; i < results.size(); i++) {
                            {
                                if (results.getJsonObject(i).getString("status").equals("COMPLETED"))
                                {
                                    if(ips.getString(j).equals(results.getJsonObject(i).getString("ip"))) {
                                        isAnyWrongIp = false;
                                        provisionsToAddFutures.add(App.provisionModel.save(new JsonArray().add(discovery_id).add(ips.getString(j))));
                                    }
                                }

                            }
                            if(isAnyWrongIp)
                            {
                                wrongIps.add(ips.getString(j));
                                isAnyWrongIp = true;
                            }
                        }

                    }

                    CompositeFuture.join(provisionsToAddFutures)
                            .onSuccess(v -> v.onSuccess(savedProvisions ->
                            {

                               /*
                                    savedProvision Is Currently In This Format...
                                    [ [ { id: 1, ip: '10.20.41.10', ... } ],  [ { id: 2, ip: '10.20.41.11', ... } ] ]

                                    We Want It In This Format...
                                    [ { id: 1, ip: '10.20.41.10', ... }, { id: 2, ip: '10.20.41.11', ... } ]

                               */

                                var provisionArray = new JsonArray();

                                for(var i = 0; i < savedProvisions.size(); i++)
                                {
                                    if(savedProvisions.resultAt(i) != null)
                                    {
                                        var savedProvision = (JsonArray) savedProvisions.resultAt(i);
                                        provisionArray.add(savedProvision.getJsonObject(0));
                                    }
                                }

                                MetricGroupCacheStore.insertProvisionArray(provisionArray);

                                HttpResponse.sendSuccess(ctx, 200, "Provisioned All Valid Ips", provisionArray);

                            }))
                            .onFailure(err -> {
                                err.printStackTrace();
                                HttpResponse.sendFailure(ctx, 500, "Error during provisioning", err.getMessage());
                            });
                }
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 400, "Discovery Not Found", ""));

    }

    public static void deleteProvision(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if discovery exists
        App.provisionModel
                .get(new JsonArray().add(id))
                .onSuccess(provision -> {
                    if (provision.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Provision not found");
                        return;
                    }

                    // Provision found, proceed with delete
                    App.provisionModel
                            .delete(new JsonArray().add(id))
                            .onSuccess(deletedDiscovery -> {
                                if(!deletedDiscovery.isEmpty()) {
                                    MetricGroupCacheStore.deleteMetricGroups(deletedDiscovery.getJsonObject(0).getInteger("id"));
                                    HttpResponse.sendSuccess(ctx, 200, "Provision deleted successfully", provision);
                                }
                                else{
                                    HttpResponse.sendFailure(ctx, 400, "Failed to delete Provision");
                                }
                            })
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));

    }

    public static void updateMetric(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        var metrics = ctx.body().asJsonObject().getJsonArray("metrics");

        List<Future<JsonArray>> updateMetricGroupsFuture = new ArrayList<>();

        for(var i = 0; i < metrics.size(); i++)
        {
            var name = metrics.getJsonObject(i).getString("name");
            var pollingInterval = metrics.getJsonObject(i).getInteger("polling_interval");
            var enable = metrics.getJsonObject(i).getBoolean("enable");

            updateMetricGroupsFuture.add(App.provisionModel.update(new JsonArray().add(id).add(pollingInterval).add(name).add(null)));
        }

        Future.join(updateMetricGroupsFuture)
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Error Updating Metric groups", err.getMessage()))
                .onSuccess(v -> App.provisionModel.get(new JsonArray().add(id))
                        .onSuccess(res -> {
                            MetricGroupCacheStore.updateMetricGroups(res.getJsonObject(0).getJsonArray("metric_groups"));
                            HttpResponse.sendSuccess(ctx, 200, "Updated Provision", res);
                        })
                        .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Failed To Update Discovery", err.getMessage())));
    }

}
