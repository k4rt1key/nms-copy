package org.nms.API.Validators;

import io.vertx.ext.web.RoutingContext;
import org.nms.API.Utility.HttpResponse;
import org.nms.API.Utility.IpHelpers;

import java.util.ArrayList;
import java.util.List;

public class ProvisionRequestValidator
{
    public static void getProvisionByIdRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        ctx.next();
    }

    public static void createProvisionRequestValidator(RoutingContext ctx)
    {
        Utility.validateBody(ctx);

        Utility.validateInputFields(ctx, new String[]{"ips", "discovery_id"}, true);

        var body = ctx.body().asJsonObject();

        var ips = body.getJsonArray("ips");

        var invalidIps = new StringBuilder();

        for(var ip: ips)
        {
            if(!IpHelpers.isValidIp(ip.toString()))
            {
                invalidIps.append(ip).append(", ");
            }
        }

        if(!invalidIps.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Invalid IPs: " + invalidIps);
            return;
        }

        if(body.getString("discovery_id").isEmpty())
        {
            HttpResponse.sendFailure(ctx, 400, "Discovery ID must be at least 1 character");

            try
            {
                Integer.parseInt(body.getString("discovery_id"));
            }
            catch (Exception e)
            {
                HttpResponse.sendFailure(ctx, 400, "Discovery ID must be integer");
            }

            return;
        }

        ctx.next();
    }

    public static void updateProvisionRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        Utility.validateBody(ctx);

        Utility.validateInputFields(ctx, new String[]{"metrics"}, true);

        var body = ctx.body().asJsonObject();

        var metrics = body.getJsonArray("metrics");

        if(metrics != null && !metrics.isEmpty())
        {
            for(var i = 0; i < metrics.size(); i++)
            {
                var type = metrics.getJsonObject(i).getString("name");

                var metricType = new ArrayList<>(List.of("CPUINFO", "CPUUSAGE", "DISK", "MEMORY", "DISK", "UPTIME", "PROCESS", "NETWORK", "SYSTEMINFO"));

                if(!metricType.contains(type))
                {
                    HttpResponse.sendFailure(ctx, 400,"Invalid Metric Type " + type);
                    return;
                }

                var interval = metrics.getJsonObject(i).getInteger("polling_interval");

                var enable = metrics.getJsonObject(i).getBoolean("enable");

                if(type == null)
                {
                    HttpResponse.sendFailure(ctx, 400,"Provide Valid Metric Type");
                    return;
                }

                if( ( (interval == null || interval < 60 || interval % 60 != 0) & enable == null ) )
                {
                    HttpResponse.sendFailure(ctx, 400,"Provide Polling Interval ( Multiple of 60 ) Or Enable ( true or false )");
                    return;
                }

            }
        }

        ctx.next();
    }

    public static void deleteProvisionRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        ctx.next();
    }
}
