package org.nms.PluginManager;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.App;
import org.nms.ConsoleLogger;
import org.nms.Constants;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PluginManager
{
    private static final int DISCOVERY_TIMEOUT = 30;

    private static final int POLLING_TIMEOUT = 30;

    public static Future<JsonArray> runDiscovery(int discoveryId, JsonArray ips, int port, JsonArray credentials)
    {
        return App.vertx.executeBlocking(() -> {
            try {
                // Step-1.1: Prepare Request Json
                JsonObject discoveryInput = new JsonObject();
                discoveryInput.put("type", "discovery");
                discoveryInput.put("id", discoveryId);
                discoveryInput.put("ips", ips);
                discoveryInput.put("port", port);
                discoveryInput.put("credentials", credentials);

                // Step-1.2: Prepare Command
                String inputJsonStr = discoveryInput.encode();
                String[] command = {Constants.PLUGIN_PATH, inputJsonStr};

                // Step-2: Run Command
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                Process process = builder.start();

                // Waiting for 20 Seconds For Reply
                boolean done = process.waitFor(DISCOVERY_TIMEOUT, TimeUnit.SECONDS);

                if(!done)
                {
                    ConsoleLogger.warn("⏱️ GoPlugin Is Not Responding Within " + DISCOVERY_TIMEOUT + " Seconds");

                    return new JsonArray();
                }
                else
                {
                    // Step-3: Read Output From Go's Stream
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String output = reader.lines().collect(Collectors.joining());

                    // Parse Response JSON
                    JsonObject outputJson = new JsonObject(output);

                    // Return Result Array
                    return outputJson.getJsonArray("result");
                }

            }
            catch (Exception e)
            {
                ConsoleLogger.error("❌ Error Running Discovery In PluginManager " + e.getMessage());

                return new JsonArray();
            }
        });
    }

    public static Future<JsonArray> runPolling(JsonArray metricGroups)
    {
        return App.vertx.executeBlocking(() ->
        {
            try
            {
                // Step-1.1: Prepare Request Json
                JsonObject pollingInput = new JsonObject();
                pollingInput.put("type", "polling");
                pollingInput.put("metric_groups", metricGroups);

                // Step-1.2: Prepare Command
                String inputJsonStr = pollingInput.encode();
                String[] command = {Constants.PLUGIN_PATH, inputJsonStr};

                // Step-2: Run Command
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                Process process = builder.start();

                // Waiting for 20 Seconds For Reply
                boolean done = process.waitFor(POLLING_TIMEOUT, TimeUnit.SECONDS);

                if(!done)
                {
                    ConsoleLogger.warn("⏱️ GoPlugin Is Not Responding Within " + POLLING_TIMEOUT + " Seconds");

                    return new JsonArray();
                }
                else
                {
                    // Step-3: Read Output From Go's Stream
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String output = reader.lines().collect(Collectors.joining());

                    // Parse Response JSON
                    JsonObject outputJson = new JsonObject(output);

                    // Return Result Array
                    return outputJson.getJsonArray("metric_groups");
                }
            }
            catch (Exception e)
            {
                ConsoleLogger.error("❌ Error Running Discovery In PluginManager " + e.getMessage());

                return new JsonArray();
            }
        });
    }

}