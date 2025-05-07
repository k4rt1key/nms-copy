package org.nms.API.Validators;

import io.vertx.ext.web.RoutingContext;
import org.nms.API.Utility.HttpResponse;

public class CredentialRequestValidator
{
    public static void getCredentialByIdRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        ctx.next();
    }

    public static void createCredentialRequestValidator(RoutingContext ctx)
    {
        Utility.validateBody(ctx);

        Utility.validateInputFields(ctx, new String[]{"name", "type", "username", "password"}, true);

        var body = ctx.body().asJsonObject();

        if(body.getString("type") != null && !body.getString("type").equals("WINRM"))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid type. Only 'WINRM' is supported for now");

            return;
        }

        ctx.next();
    }

    public static void updateCredentialByIdRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        Utility.validateBody(ctx);

        Utility.validateInputFields(ctx, new String[]{"name", "type", "username", "password"}, false);

        var body = ctx.body().asJsonObject();

        if(body.getString("type") != null && !body.getString("type").equals("WINRM"))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid type. Only 'WINRM' is supported for now");
            return;
        }

        ctx.next();
    }

    public static void deleteCredentialByIdRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        ctx.next();
    }
}