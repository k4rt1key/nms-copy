package org.nms.API.Validators;

import io.vertx.ext.web.RoutingContext;
import org.nms.API.Utility.HttpResponse;
import org.nms.API.Utility.IpHelpers;

/**
 * Validates requests to discovery-related endpoints
 */
public class DiscoveryRequestValidator
{
    public static void getDiscoveryByIdRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        ctx.next();
    }

    public static void createDiscoveryRequestValidator(RoutingContext ctx)
    {

        Utility.validateBody(ctx);

        Utility.validateInputFields(ctx, new String[]{"name", "ip", "ip_type", "credentials", "port"}, true);

        var body = ctx.body().asJsonObject();

        var credentials = body.getJsonArray("credentials");

        if (credentials == null || credentials.isEmpty())
        {
            HttpResponse.sendFailure(ctx, 400, "Missing or empty 'credentials' field");
            return;
        }

        for (var credential : credentials)
        {
            try
            {
                Integer.parseInt(credential.toString());
            }
            catch (NumberFormatException e)
            {
                HttpResponse.sendFailure(ctx, 400, "Invalid credential ID: " + credential);
                return;
            }
        }

        var port = body.getInteger("port");

        if (port < 1 || port > 65535)
        {
            HttpResponse.sendFailure(ctx, 400, "Port must be between 1 and 65535");
            return;
        }

        var ipType = body.getString("ip_type");

        if (!ipType.equals("SINGLE") && !ipType.equals("RANGE") && !ipType.equals("SUBNET"))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid IP type. Only 'SINGLE', 'RANGE' and 'SUBNET' are supported");
            return;
        }

        var ip = body.getString("ip");

        if (!IpHelpers.isValidIpAndType(ip, ipType))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid IP address or mismatch Ip and IpType: " + ip + ", " + ipType);
            return;
        }

        ctx.next();
    }

    public static void updateDiscoveryRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        Utility.validateBody(ctx);

        Utility.validateInputFields(ctx, new String[]{"name", "ip", "ip_type", "port"}, false);

        Utility.validatePort(ctx);

        var ipType = ctx.body().asJsonObject().getString("ip_type");

        if (ipType != null && !ipType.equals("SINGLE") && !ipType.equals("RANGE") && !ipType.equals("SUBNET"))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid IP type. Only 'SINGLE', 'RANGE' and 'SUBNET' are supported");
        }

        var ip = ctx.body().asJsonObject().getString("ip");

        if (ip != null && ipType != null && !IpHelpers.isValidIpAndType(ip, ipType))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid IP address or mismatch Ip and IpType: " + ip + ", " + ipType);
            return;
        }

        ctx.next();
    }

    public static void updateDiscoveryCredentialsRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        Utility.validateBody(ctx);

        var body = ctx.body().asJsonObject();

        var add_credentials = body.getJsonArray("add_credentials");

        var remove_credentials = body.getJsonArray("remove_credentials");

        if (
                (add_credentials == null || add_credentials.isEmpty()) &&
                (remove_credentials == null || remove_credentials.isEmpty())
        )
        {
            HttpResponse.sendFailure(ctx, 400, "Missing or empty 'add_credentials' and 'remove_credentials' fields");
            return;
        }

        if (add_credentials != null && !add_credentials.isEmpty())
        {
            for (var credential : add_credentials)
            {
                try
                {
                    Integer.parseInt(credential.toString());
                }
                catch (NumberFormatException e)
                {
                    HttpResponse.sendFailure(ctx, 400, "Invalid credential ID in add_credentials: " + credential);
                    return;
                }
            }
        }

        if (remove_credentials != null && !remove_credentials.isEmpty())
        {
            for (var credential : remove_credentials)
            {
                try
                {
                    Integer.parseInt(credential.toString());
                }
                catch (NumberFormatException e)
                {
                    HttpResponse.sendFailure(ctx, 400, "Invalid credential ID in remove_credentials: " + credential);
                    return;
                }
            }
        }

        ctx.next();
    }

    public static void runDiscoveryRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        ctx.next();
    }

    public static void deleteDiscoveryRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        ctx.next();
    }
}