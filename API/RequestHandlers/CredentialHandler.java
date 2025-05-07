package org.nms.API.RequestHandlers;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import org.nms.App;
import org.nms.API.Utility.HttpResponse;

public class CredentialHandler
{
    public static void getAllCredentials(RoutingContext ctx)
    {
        App.credentialModel
                .getAll()
                .onSuccess(credentials ->
                {
                    // Credentials not found
                    if(credentials.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "No credentials found");
                        return;
                    }

                    // Credentials Found
                    HttpResponse.sendSuccess(ctx, 200, "Credentials found", credentials);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void getCredentialById(RoutingContext ctx)
    {
        int id = Integer.parseInt(ctx.request().getParam("id"));

        App.credentialModel
                .get(new JsonArray().add(id))
                .onSuccess(credential ->
                {
                    // Credential not found
                    if (credential.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Credential not found");
                        return;
                    }

                    // Credential found
                    HttpResponse.sendSuccess(ctx, 200, "Credential found", credential);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void createCredential(RoutingContext ctx)
    {
        var name = ctx.body().asJsonObject().getString("name");
        var type = ctx.body().asJsonObject().getString("type");
        var username = ctx.body().asJsonObject().getString("username");
        var password = ctx.body().asJsonObject().getString("password");

        App.credentialModel
                .save(new JsonArray()
                        .add(name)
                        .add(type)
                        .add(username)
                        .add(password)
                )
                .onSuccess(credential ->
                {
                    // Credential in response not found
                    if (credential.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 400, "Cannot create credential");
                        return;
                    }

                    HttpResponse.sendSuccess(ctx, 201, "Credential created", credential);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void updateCredential(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if credential exists
        App.credentialModel
                .get(new JsonArray().add(id))
                .onSuccess(credential ->
                {
                    // Credential not found
                    if (credential.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Credential not found");
                        return;
                    }

                    // Credential found, proceed with update
                    var name = ctx.body().asJsonObject().getString("name");
                    var type = ctx.body().asJsonObject().getString("type");
                    var username = ctx.body().asJsonObject().getString("username");
                    var password = ctx.body().asJsonObject().getString("password");

                    App.credentialModel
                            .update(new JsonArray()
                                    .add(id)
                                    .add(name)
                                    .add(type)
                                    .add(username)
                                    .add(password)
                            )
                            .onSuccess(res -> HttpResponse.sendSuccess(ctx, 200, "Credential updated successfully", res))
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void deleteCredential(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if credential exists
        App.credentialModel
                .get(new JsonArray().add(id))
                .onSuccess(credential ->
                {
                    // Credential not found
                    if (credential.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Credential not found");
                        return;
                    }

                    // Credential found, proceed with delete
                    App.credentialModel
                            .delete(new JsonArray().add(id))
                            .onSuccess(res -> HttpResponse.sendSuccess(ctx, 200, "Credential deleted successfully", credential))
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }
}