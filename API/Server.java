package org.nms.API;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.nms.App;
import org.nms.ConsoleLogger;
import org.nms.API.RequestHandlers.*;
import org.nms.API.Middlewares.AuthMiddleware;
import org.nms.API.Validators.CredentialRequestValidator;
import org.nms.API.Validators.DiscoveryRequestValidator;
import org.nms.API.Validators.ProvisionRequestValidator;
import org.nms.API.Validators.UserRequestValidator;

public class Server extends AbstractVerticle
{

    public static final String CREDENTIALS_ENDPOINT = "/api/v1/credential/*";

    public static final String DISCOVERY_ENDPOINT = "/api/v1/discovery/*";

    public static final String PROVISION_ENDPOINT = "/api/v1/provision/*";

    public static final String POLLING_ENDPOINT = "/api/v1/polling/*";

    public static final String USER_ENDPOINT = "/api/v1/user/*";

    public static final int HTTP_PORT = 8080;

    @Override
    public void start(Promise<Void> startPromise)
    {

        var server = App.vertx.createHttpServer(new HttpServerOptions().setReuseAddress(true));

        var router = Router.router(App.vertx);

        router.route().handler(BodyHandler.create());

        router.route().handler(ctx ->
        {
            ctx.response().putHeader("Content-Type", "application/json");
            
            ctx.next();
        });

        router.route(USER_ENDPOINT).subRouter(UserRouter.router());

        router.route()
                .handler(JWTAuthHandler.create(JwtConfig.jwtAuth))
                .handler(AuthMiddleware::authenticate);

        router.route(CREDENTIALS_ENDPOINT).subRouter(CredentialRouter.router());

        router.route(DISCOVERY_ENDPOINT).subRouter(DiscoveryRouter.router());

        router.route(PROVISION_ENDPOINT).subRouter(ProvisionRouter.router());

        router.route(POLLING_ENDPOINT).subRouter(PollingRouter.router());

        server.requestHandler(router);


        server.listen(HTTP_PORT, http ->
        {
            if(http.succeeded())
            {
                ConsoleLogger.info("✅ HTTP Server Started On Port => " + HTTP_PORT + " On Thread [ " + Thread.currentThread().getName() + " ] ");

                startPromise.complete();
            }
            else
            {
                ConsoleLogger.error("Failed To Start HTTP Server => " + http.cause());

                startPromise.fail(http.cause());
            }
        });

    }

    @Override
    public void stop()
    {
        ConsoleLogger.info("Http Server Stopped");
    }
}

class UserRouter
{

    public static Router router()
    {
        var router = Router.router(App.vertx);

        router.get("/")
                .handler(UserHandler::getUsers);


        router.get("/:id")
                .handler(JWTAuthHandler.create(JwtConfig.jwtAuth))
                .handler(AuthMiddleware::authenticate)
                .handler(UserRequestValidator::getUserByIdRequestValidator)
                .handler(UserHandler::getUserById);


        router.post("/login")
                .handler(UserRequestValidator::loginRequestValidator)
                .handler(UserHandler::login);

        router.post("/register")
                .handler(UserRequestValidator::registerRequestValidator)
                .handler(UserHandler::register);


        router.patch("/:id")
                .handler(JWTAuthHandler.create(JwtConfig.jwtAuth))
                .handler(AuthMiddleware::authenticate)
                .handler(UserRequestValidator::updateUserRequestValidator)
                .handler(UserHandler::updateUser);

        router.delete("/:id")
                .handler(JWTAuthHandler.create(JwtConfig.jwtAuth))
                .handler(AuthMiddleware::authenticate)
                .handler(UserRequestValidator::deleteUserRequestValidator)
                .handler(UserHandler::deleteUser);

        return router;
    }
}

class ProvisionRouter
{
    public static Router router()
    {
        var router = Router.router(App.vertx);

        router.get("/")
                .handler(JWTAuthHandler.create(JwtConfig.jwtAuth))
                .handler(ProvisionHandler::getAllProvisions);

        router.get("/:id")
                .handler(ProvisionRequestValidator::getProvisionByIdRequestValidator)
                .handler(ProvisionHandler::getProvisionById);

        router.post("/")
                .handler(ProvisionRequestValidator::createProvisionRequestValidator)
                .handler(ProvisionHandler::createProvision);


        router.patch("/:id")
                .handler(ProvisionRequestValidator::updateProvisionRequestValidator)
                .handler(ProvisionHandler::updateMetric);

        router.delete("/:id")
                .handler(ProvisionRequestValidator::deleteProvisionRequestValidator)
                .handler(ProvisionHandler::deleteProvision);

        return router;
    }
}

class DiscoveryRouter
{
    public static Router router()
    {
        var router = Router.router(App.vertx);

        router.get("/results/:id")
                .handler(DiscoveryRequestValidator::getDiscoveryByIdRequestValidator)
                .handler(DiscoveryHandler::getDiscoveryResultsById);

        router.get("/results")
                .handler(DiscoveryHandler::getDiscoveryResults);

        router.get("/")
                .handler(DiscoveryHandler::getAllDiscoveries);


        router.get("/:id")
                .handler(DiscoveryRequestValidator::getDiscoveryByIdRequestValidator)
                .handler(DiscoveryHandler::getDiscoveryById);

        router.post("/")
                .handler(DiscoveryRequestValidator::createDiscoveryRequestValidator)
                .handler(DiscoveryHandler::createDiscovery);

        router.post("/run/:id")
                .handler(DiscoveryRequestValidator::runDiscoveryRequestValidator)
                .handler(DiscoveryHandler::runDiscovery);

        router.patch("/:id")
                .handler(DiscoveryRequestValidator::updateDiscoveryRequestValidator)
                .handler(DiscoveryHandler::updateDiscovery);

        router.patch("/credential/:id")
                .handler(DiscoveryRequestValidator::updateDiscoveryCredentialsRequestValidator)
                .handler(DiscoveryHandler::updateDiscoveryCredentials);

        router.delete("/:id")
                .handler(DiscoveryRequestValidator::deleteDiscoveryRequestValidator)
                .handler(DiscoveryHandler::deleteDiscovery);

        return router;
    }
}

class CredentialRouter
{
    public static Router router()
    {
        var router = Router.router(App.vertx);

        router.get("/")
                .handler(CredentialHandler::getAllCredentials);

        router.get("/:id")
                .handler(CredentialRequestValidator::getCredentialByIdRequestValidator)
                .handler(CredentialHandler::getCredentialById);

        router.post("/")
                .handler(CredentialRequestValidator::createCredentialRequestValidator)
                .handler(CredentialHandler::createCredential);

        router.patch("/:id")
                .handler(CredentialRequestValidator::updateCredentialByIdRequestValidator)
                .handler(CredentialHandler::updateCredential);

        router.delete("/:id")
                .handler(CredentialRequestValidator::deleteCredentialByIdRequestValidator)
                .handler(CredentialHandler::deleteCredential);

        return router;
    }
}

class PollingRouter
{
    public static Router router()
    {
        var router = Router.router(App.vertx);

        router.get("/")
                .handler(MetricResultHandler::getAllPolledData);

        return router;
    }
}