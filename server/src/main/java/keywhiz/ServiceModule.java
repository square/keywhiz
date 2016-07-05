/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package keywhiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.Configuration;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.java8.auth.Authenticator;
import io.dropwizard.setup.Environment;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import keywhiz.auth.BouncyCastle;
import keywhiz.auth.User;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.auth.cookie.CookieModule;
import keywhiz.auth.cookie.SessionCookie;
import keywhiz.auth.xsrf.Xsrf;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.CryptoModule;
import keywhiz.service.crypto.SecretTransformer;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import keywhiz.utility.DSLContexts;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Base64.getEncoder;
import static keywhiz.JooqHealthCheck.OnFailure.LOG_ONLY;
import static keywhiz.JooqHealthCheck.OnFailure.RETURN_UNHEALTHY;

public class ServiceModule extends AbstractModule {
  private static final Logger logger = LoggerFactory.getLogger(ServiceModule.class);
  private final Environment environment;
  private final KeywhizConfig config;
  private final SecureRandom random;

  public ServiceModule(KeywhizConfig config, Environment environment) {
    this.config = checkNotNull(config);
    this.environment = checkNotNull(environment);
    this.random = new SecureRandom();
  }

  @Override protected void configure() {
    // Initialize the BouncyCastle security provider for cryptography support.
    BouncyCastle.require();

    bind(Clock.class).toInstance(Clock.systemUTC());

    String cookieKey = config.getCookieKey();
    if (cookieKey == null) {
      cookieKey = generateCookieKey();
      logger.warn("No cookieKey set in config, generating a random cookieKey for use: %s", cookieKey);
    }
    install(new CookieModule(cookieKey));
    install(new CryptoModule(config.getDerivationProviderClass(), config.getContentKeyStore()));

    bind(CookieConfig.class).annotatedWith(SessionCookie.class)
        .toInstance(config.getSessionCookieConfig());
    bind(CookieConfig.class).annotatedWith(Xsrf.class)
        .toInstance(config.getXsrfCookieConfig());

    // TODO(justin): Consider https://github.com/HubSpot/dropwizard-guice.
    bind(Environment.class).toInstance(environment);
    bind(Configuration.class).toInstance(config);
    bind(KeywhizConfig.class).toInstance(config);
  }

  private String generateCookieKey() {
    String cookieKey;
    byte[] cookieKeyBytes = new byte[32];
    random.nextBytes(cookieKeyBytes);
    cookieKey = new String(getEncoder().encode(cookieKeyBytes));
    return cookieKey;
  }

  // ManagedDataSource

  @Provides @Singleton ManagedDataSource dataSource(Environment environment,
      KeywhizConfig config) {
    DataSourceFactory dataSourceFactory = config.getDataSourceFactory();
    ManagedDataSource dataSource = dataSourceFactory.build(environment.metrics(), "db-writable");
    environment.lifecycle().manage(dataSource);

    environment.healthChecks().register("db-read-write-health",
        new JooqHealthCheck(dataSource, LOG_ONLY));

    return dataSource;
  }

  @Provides @Singleton @Readonly ManagedDataSource readonlyDataSource(Environment environment,
      KeywhizConfig config) {
    DataSourceFactory dataSourceFactory = config.getReadonlyDataSourceFactory();
    ManagedDataSource dataSource = dataSourceFactory.build(environment.metrics(), "db-readonly");
    environment.lifecycle().manage(dataSource);

    environment.healthChecks().register("db-readonly-health",
        new JooqHealthCheck(dataSource, RETURN_UNHEALTHY));

    return dataSource;
  }

  @Provides ObjectMapper configuredObjectMapper(Environment environment) {
    return environment.getObjectMapper();
  }

  // jOOQ

  @Provides @Singleton DSLContext jooqContext(ManagedDataSource dataSource) throws SQLException {
    return DSLContexts.databaseAgnostic(dataSource);
  }

  @Provides @Singleton
  @Readonly DSLContext readonlyJooqContext(@Readonly ManagedDataSource dataSource)
      throws SQLException {
    return DSLContexts.databaseAgnostic(dataSource);
  }

  @Provides @Singleton SecretController secretController(SecretTransformer transformer,
      ContentCryptographer cryptographer, SecretDAOFactory secretDAOFactory) {
    return new SecretController(transformer, cryptographer, secretDAOFactory.readwrite());
  }

  @Provides @Singleton
  @Readonly SecretController readonlySecretController(SecretTransformer transformer,
      ContentCryptographer cryptographer, SecretDAOFactory secretDAOFactory) {
    return new SecretController(transformer, cryptographer, secretDAOFactory.readonly());
  }

  @Provides @Singleton
  @Readonly Authenticator<BasicCredentials, User> authenticator(KeywhizConfig config,
      @Readonly DSLContext jooqContext) {
    return config.getUserAuthenticatorFactory().build(jooqContext);
  }
}
