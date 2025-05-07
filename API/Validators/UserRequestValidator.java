package org.nms.API.Validators;

import io.vertx.ext.web.RoutingContext;

public class UserRequestValidator
{

    public static void getUserByIdRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        ctx.next();
    }

    public static void registerRequestValidator(RoutingContext ctx)
    {

        Utility.validateBody(ctx);

        Utility.validateInputFields(ctx, new String[]{"username", "password"}, true);

        ctx.next();
    }

    public static void loginRequestValidator(RoutingContext ctx)
    {
        Utility.validateBody(ctx);

        Utility.validateInputFields(ctx, new String[]{"username", "password"}, true);

        ctx.next();
    }

    public static void updateUserRequestValidator(RoutingContext ctx)
    {

        Utility.validateID(ctx);

        Utility.validateBody(ctx);

        Utility.validateInputFields(ctx, new String[]{"username", "password"}, false);

        ctx.next();
    }


    public static void deleteUserRequestValidator(RoutingContext ctx)
    {
        Utility.validateID(ctx);

        ctx.next();
    }
}
