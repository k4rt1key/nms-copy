package org.nms.API.RequestHandlers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.nms.App;
import org.nms.API.JwtConfig;
import org.nms.API.Utility.HttpResponse;

public class UserHandler
{
    public static void getUsers(RoutingContext ctx)
    {
        App.userModel
                .getAll()
                .onSuccess(users ->
                {
                    // User not found
                    if(users.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "No users found");
                        return;
                    }

                    // User Found
                    HttpResponse.sendSuccess(ctx, 200, "Users found", users);
                });
    }

    public static void getUserById(RoutingContext ctx)
    {
        int id = Integer.parseInt(ctx.request().getParam("id"));

        App.userModel
                .get(new JsonArray().add(id))
                .onSuccess(user ->
                {
                    // User not found
                    if (user.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "User not found");
                        return;
                    }

                    // User found
                    HttpResponse.sendSuccess(ctx, 200, "User found", user);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void register(RoutingContext ctx)
    {
        // Step : 1 - Check if user exists or not
        App.userModel
                .getByName(new JsonArray().add(ctx.body().asJsonObject().getString("username")))
                .compose(user ->
                {
                    // User already exists
                    if (user != null && !user.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 409, "User already exists");

                        return Future.failedFuture(new Exception("User Already Exist"));
                    }

                    return Future.succeededFuture();

                })
                .compose(v ->

                        // Step : 2 - Register user
                        App.userModel.save(new JsonArray()
                                        .add(ctx.body().asJsonObject().getString("username"))
                                        .add(ctx.body().asJsonObject().getString("password"))
                        ))

                .onSuccess(user ->
                {
                    // User in response not found
                    if (user.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 400, "Cannot register user");
                        return;
                    }

                    // Step - 3 : If Registered, Generating Auth Token
                    var jwtToken = JwtConfig.jwtAuth
                            .generateToken(new JsonObject().put("id" ,user.getJsonObject(0).getInteger("id")));

                    // Adding token to response
                    user.getJsonObject(0).put("jwt", jwtToken);

                    HttpResponse.sendSuccess(ctx, 201, "User registered", user);

                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void login(RoutingContext ctx)
    {
        var username = ctx.body().asJsonObject().getString("username");

        // Step - 1 : Check if user exists or not
        App.userModel
                .getByName(new JsonArray().add(username))
                .onSuccess(user ->
                {
                    // User not found
                    if(user == null || user.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "User not found");
                        return;
                    }

                    // Step - 2 : If existed, Validate password
                    var password = ctx.body().asJsonObject().getString("password");

                    if(!user.getJsonObject(0).getString("password").equals(password))
                    {
                        HttpResponse.sendFailure(ctx, 401, "Invalid password");
                        return;
                    }

                    // Step - 3 : If password is valid, Generate JWT token
                    var jwtToken = JwtConfig.jwtAuth.generateToken(new JsonObject()
                            .put("id", user.getJsonObject(0).getInteger("id")));

                    user.getJsonObject(0).put("jwt", jwtToken);

                    HttpResponse.sendSuccess(ctx, 200, "User logged in", user);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));

    }


    public static void updateUser(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        var username = ctx.body().asJsonObject().getString("username");

        var email = ctx.body().asJsonObject().getString("email");

        var password = ctx.body().asJsonObject().getString("password");

        App.userModel
                .update(new JsonArray()
                        .add(id)
                        .add(username)
                        .add(email)
                        .add(password)
                )
                .onSuccess(res -> HttpResponse.sendSuccess(ctx, 200, "User updated successfully", res))
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void deleteUser(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));


        // Step - 1 : Check if user exists or not
        App.userModel
                .get(new JsonArray().add(id))
                .onSuccess(res ->
                {
                    // User not found
                    if (res.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "User not found");
                        return;
                    }

                    // Check if user is authorized to delete this user
                    var loggedInUser = ctx.user().principal().getInteger("id");

                    if(id != loggedInUser)
                    {
                        HttpResponse.sendFailure(ctx, 403, "You are not authorized to delete this user");
                        return;
                    }

                    // Step - 2 : If existed, Delete user
                    App.userModel
                            .delete(new JsonArray().add(id))
                            .onSuccess( delRes -> HttpResponse.sendSuccess(ctx, 200, "User deleted Successfully", res))
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Error deleting user: " + err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }
}
