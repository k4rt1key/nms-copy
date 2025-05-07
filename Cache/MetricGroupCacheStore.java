package org.nms.Cache;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.App;
import org.nms.ConsoleLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricGroupCacheStore
{
    private static final ConcurrentHashMap<Integer, JsonObject> cachedMetricGroups = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Integer, JsonObject> referencedMetricGroups = new ConcurrentHashMap<>();

    private static final String PROVISION_PROFILE_ID = "id";

    private static final String POLLING_INTERVAL = "polling_interval";

    private static final String METRIC_GROUPS = "metric_groups";

    private static final String PROVISION_PROFILE_ID_IN_METRIC_GROUPS = "provision_profile_id";

    private static final String PORT = "port";

    private static final String METRIC_GROUPS_ID = "id";

    private static final String CREDENTIALS = "credentials";

    private static final String IP = "ip";

    private static final String NAME = "name";

    private static final String ENABLE = "enable";

    /**
     * Populates cache into db
     */
    public static Future<JsonArray> populate()
    {

        return App.provisionModel
                .getAll()
                .onSuccess(provisionArray ->
                {

                    if(provisionArray.isEmpty())
                    {
                        return;
                    }

                    insertProvisionArray(provisionArray);

                });
    }

    /**
     * Adds Provisioned Objects into cache
     * @param provisionArray array of provision object present in DB
     */
    public static void insertProvisionArray(JsonArray provisionArray)
    {
        // Iterate Over All Provisions
        for(var i = 0; i < provisionArray.size(); i++)
        {
            var provisionObject = provisionArray.getJsonObject(i);

            // Iterate Over All Metric Group Of Particular Provision
            for(var k = 0; k < provisionObject.getJsonArray(METRIC_GROUPS).size(); k++)
            {

                var metricObject = provisionObject.getJsonArray(METRIC_GROUPS).getJsonObject(k);

                var value = new JsonObject()
                        .put(METRIC_GROUPS_ID, metricObject.getInteger(METRIC_GROUPS_ID))
                        .put(PROVISION_PROFILE_ID_IN_METRIC_GROUPS, provisionObject.getInteger(PROVISION_PROFILE_ID))
                        .put(PORT, Integer.valueOf(provisionObject.getString(PORT)))
                        .put(CREDENTIALS, provisionObject.getJsonObject(CREDENTIALS).copy())
                        .put(IP, provisionObject.getString(IP))
                        .put(NAME, metricObject.getString(NAME))
                        .put(POLLING_INTERVAL, metricObject.getInteger(POLLING_INTERVAL))
                        .put(ENABLE, metricObject.getBoolean(ENABLE));

                var key = provisionObject.getJsonArray(METRIC_GROUPS).getJsonObject(k).getInteger(METRIC_GROUPS_ID);

                referencedMetricGroups.put(key, value.copy());
                cachedMetricGroups.put(key, value.copy());
            }
        }

        ConsoleLogger.info("\uD83D\uDCE9 Inserted " + provisionArray.size() + " Provisions Into Cache, Now Total Number Of Entry In Cache Is " + cachedMetricGroups.size());
    }

    /**
     * Updates MetricGroup present into cache
     */
    public static void updateMetricGroups(JsonArray metricGroups)
    {

        for(var i = 0; i < metricGroups.size(); i++)
        {
            var metricGroup = metricGroups.getJsonObject(i);

            var key =  metricGroup.getInteger(METRIC_GROUPS_ID);

            var updatedValue = referencedMetricGroups.get(key).copy();

            if(metricGroup.getInteger(POLLING_INTERVAL) != null)
            {
                updatedValue.put(POLLING_INTERVAL, metricGroup.getInteger(POLLING_INTERVAL));
            }

            if(metricGroup.getBoolean(ENABLE) != null && !metricGroup.getBoolean(ENABLE))
            {
                referencedMetricGroups.remove(key);

                cachedMetricGroups.remove(key);
            }

            referencedMetricGroups
                    .put(
                            key,
                            updatedValue
                    );

            cachedMetricGroups
                    .put(
                            key,
                            updatedValue
                    );
        }

        ConsoleLogger.info("➖ Updated " + metricGroups.size() + " Entries From Cache");
    }

    /**
     * Deletes MetricGroup Present Into Cache
     */
    public static void deleteMetricGroups(Integer provisionId)
    {
        AtomicInteger total = new AtomicInteger();

        referencedMetricGroups.forEach((key, value)->{
            if(value.getInteger(PROVISION_PROFILE_ID_IN_METRIC_GROUPS).equals(provisionId))
            {
                referencedMetricGroups.remove(key);

                cachedMetricGroups.remove(key);

                total.incrementAndGet();
            }
        });

        ConsoleLogger.info("➖ Removed " + total.get() + " Entries From Cache");
    }

    /**
     * Gets Metric Groups That Have Timed Out And Need Polling
     * Returns Timed-Out Metric Groups And Resets Their Polling Intervals
     * From Reference Values
     */
    public static List<JsonObject> getTimedOutMetricGroups()
    {
        List<JsonObject> timedOutMetricGroups = new ArrayList<>();

        // Iterate Over All Metric Groups And Decrement Interval
        cachedMetricGroups.forEach((key, value) ->
        {
            Integer pollingInterval = value.getInteger(POLLING_INTERVAL, 0);

            if (pollingInterval <= 0)
            {
                // Found Timed Out Metric Group
                timedOutMetricGroups.add(value.copy());

                var updated = value.copy().put(POLLING_INTERVAL, referencedMetricGroups.get(key).getInteger(POLLING_INTERVAL));

                // Reset Its Interval
                cachedMetricGroups.put(
                        key,
                        updated.copy()
                );
            }
        });

        timedOutMetricGroups.forEach((timedOutMetricGroup ->
        {
            var key = timedOutMetricGroup.getInteger(METRIC_GROUPS_ID);

            var updated = timedOutMetricGroup.copy().put(POLLING_INTERVAL, referencedMetricGroups.get(key).getInteger(POLLING_INTERVAL));

            // Reset Its Interval
            cachedMetricGroups.put(
                    key,
                    updated.copy()
            );
        }));

        ConsoleLogger.debug("⏰ Found " + timedOutMetricGroups.size() + " Timed Out Metric Groups");

        return timedOutMetricGroups;
    }

    /**
     * Decrement Interval Of All Entries
     */
    public static void decrementMetricGroupInterval(int interval)
    {
        cachedMetricGroups.forEach((key, value) ->
        {
            Integer currentInterval = value.getInteger(POLLING_INTERVAL, 0);

            Integer updatedInterval =  Math.max(0, currentInterval - interval);

            cachedMetricGroups.put(
                key,
                cachedMetricGroups.get(key).put(POLLING_INTERVAL, updatedInterval)
            );
        });
    }

}