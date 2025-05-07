package org.nms.API.Validators;

import io.vertx.ext.web.RoutingContext;
import org.nms.API.Utility.HttpResponse;

public class Utility
{

    public static void validateID(RoutingContext ctx)
    {
        var id = ctx.request().getParam("id");

        if (id == null || id.isEmpty())
        {
            HttpResponse.sendFailure(ctx, 400,"Missing or empty 'id' parameter");
            return;
        }

        try
        {
            Integer.parseInt(id);
        }
        catch (IllegalArgumentException e)
        {
            HttpResponse.sendFailure(ctx, 400,"Invalid 'id' parameter. Must be a positive integer");
        }
    }

    public static void validateBody(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if (body == null || body.isEmpty())
        {
            HttpResponse.sendFailure(ctx, 400,"Missing or empty request body");
        }
    }

    public static void validateInputFields(RoutingContext ctx, String[] requiredFields, boolean isAllRequired)
    {

        var body = ctx.body().asJsonObject();

        var missingFields = new StringBuilder();

        var isAllMissing = true;

        for (String requiredField : requiredFields)
        {
            if (body.getString(requiredField) == null)
            {
                isAllMissing = false;

                missingFields
                        .append(requiredField)
                        .append(", ");
            }
        }

        if(isAllRequired && !missingFields.isEmpty())
        {
            HttpResponse.sendFailure(ctx, 400,"Missing required fields: " + missingFields);
        }

        if(!isAllRequired && isAllMissing)
        {
            HttpResponse.sendFailure(ctx, 400,"Missing required fields: " + missingFields);
        }
    }

    public static void validatePort(RoutingContext ctx)
    {
        var port = ctx.body().asJsonObject().getInteger("port");

        if (port != null && (port < 1 || port > 65535))
        {
            HttpResponse.sendFailure(ctx, 400, "Port must be between 1 and 65535");
        }
    }
}
