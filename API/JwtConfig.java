package org.nms.API;

import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.nms.Constants;

import static org.nms.App.vertx;

public class JwtConfig
{

    public static JWTAuthOptions config = new JWTAuthOptions()
                                            .setKeyStore(new KeyStoreOptions()
                                                    .setPath(Constants.KEYSTORE_PATH)
                                                    .setPassword(Constants.JWT_SECRET))
                                            .setJWTOptions(new JWTOptions()
                                                    .setExpiresInMinutes(3600));

    public static JWTAuth jwtAuth = JWTAuth.create(vertx, config);

}
