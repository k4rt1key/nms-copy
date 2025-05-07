package org.nms.Scheduler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.nms.App;
import org.nms.Cache.MetricGroupCacheStore;
import org.nms.ConsoleLogger;
import org.nms.Database.Models.MetricResultModel;
import org.nms.PluginManager.PluginManager;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Scheduler extends AbstractVerticle
{
    private final int CHECKING_INTERVAL = 10;

    private long timerId;

    private final MetricResultModel polledDataService = MetricResultModel.getInstance();


    /**
     * Starts the scheduler
     */
    @Override
    public void start()
    {
        ConsoleLogger.debug("✅ Starting SchedulerVerticle With Checking Interval => " + CHECKING_INTERVAL + " Seconds On Thread [ " + Thread.currentThread().getName() + " ] ");

        MetricGroupCacheStore
                .populate()
                .onSuccess((res)-> timerId = App.vertx.setPeriodic(CHECKING_INTERVAL * 1000, id -> processMetricGroups()))
                .onFailure(err -> ConsoleLogger.error("❌ Error Running Scheduler => " + err.getMessage()));

    }

    /**
     * Stops the scheduler
     */
    @Override
    public void stop()
    {
        if (timerId != 0)
        {
            App.vertx.cancelTimer(timerId);
            ConsoleLogger.debug("\uD83D\uDED1 Scheduler Stopped");
            timerId = 0;
        }
    }

    /**
     * Process metric groups, decrementing intervals and handling timed-out groups
     */
    private void processMetricGroups()
    {
        // Step-1: Decrement Intervals By xyz Seconds In Cache
        MetricGroupCacheStore.decrementMetricGroupInterval(CHECKING_INTERVAL);

        // Step-2: After Decrementing Check For Timed-Out MetricGroups
        List<JsonObject> timedOutGroups = MetricGroupCacheStore.getTimedOutMetricGroups();

        // Step-3: If There are Timed-out MetricGroups Ready For Polling...
        if (!timedOutGroups.isEmpty()) {
            // Step-4: Format Request For Polling
            JsonArray metricGroups = preparePollingMetricGroups(timedOutGroups);

            // Step-5: Send Request to PluginManager
            // If Success : Process & Save Result
            PluginManager
                    .runPolling(metricGroups)
                    .onSuccess(this::processAndSaveResults)
                    .onFailure(err -> ConsoleLogger.error("❌ Error During Polling => " + err.getMessage()));
        }
    }

    /**
     * Prepare the request for polling
     * @param timedOutGroups List of timed-out metric groups
     * @return JsonArray with formatted metric groups request
     */
    private JsonArray preparePollingMetricGroups(List<JsonObject> timedOutGroups)
    {
        JsonArray metricGroups = new JsonArray();

        for (JsonObject metricGroup : timedOutGroups)
        {
            JsonObject groupData = new JsonObject()
                    .put("provision_profile_id", metricGroup.getInteger("provision_profile_id"))
                    .put("name", metricGroup.getString("name"))
                    .put("ip", metricGroup.getString("ip"))
                    .put("port", metricGroup.getInteger("port"))
                    .put("credentials", metricGroup.getJsonObject("credentials"));

            metricGroups.add(groupData);
        }

        return metricGroups;
    }

    /**
     * Process plugin manager's response and save the results in DB
     * @param results The results from plugin manager
     */
    private void processAndSaveResults(JsonArray results)
    {
        List<Tuple> batchParams = new ArrayList<>();

        for (int i = 0; i < results.size(); i++)
        {
            JsonObject result = results.getJsonObject(i);

            // Step-1: Skip unsuccessful results
            if (!result.getBoolean("success", false))
            {
                continue;
            }

            // Step-2: Add Timestamp
            result.put("time", ZonedDateTime.now().toString());

            // Step-3: Format Data Into JsonArray or JsonObject
            if(!getJsonObject(result.getString("data")).isEmpty())
            {
                batchParams.add(Tuple.of(
                        result.getInteger("provision_profile_id"),
                        result.getString("name"),
                        getJsonObject(result.getString("data"))
                ));
            }
            else if(!getJsonArray(result.getString("data")).isEmpty())
            {
                batchParams.add(Tuple.of(
                        result.getInteger("provision_profile_id"),
                        result.getString("name"),
                        getJsonArray(result.getString("data"))
                ));
            }
            else {
                batchParams.add(Tuple.of(
                        result.getInteger("provision_profile_id"),
                        result.getString("name"),
                        result.getString("data")
                ));
            }
        }

        // Step-4: Save Into DB
        if (!batchParams.isEmpty())
        {
            polledDataService.save(batchParams)
                    .onFailure(err -> ConsoleLogger.error("❌ Error During Saving Polled Data  => " + err.getMessage()));
        }
    }

    private JsonObject getJsonObject(String s)
    {
        try
        {
            return new JsonObject(s);
        }
        catch (Exception e)
        {
            return new JsonObject();
        }
    }

    private JsonArray getJsonArray(String s)
    {
        try
        {
            return new JsonArray(s);

        }
        catch (Exception e)
        {
            return new JsonArray();
        }
    }
}