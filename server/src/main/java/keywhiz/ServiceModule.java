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
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.setup.Environment;
import java.sql.SQLException;
import java.time.Clock;
import keywhiz.auth.BouncyCastle;
import keywhiz.auth.User;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.auth.cookie.CookieModule;
import keywhiz.auth.cookie.SessionCookie;
import keywhiz.auth.xsrf.Xsrf;
import keywhiz.log.AuditLog;
import keywhiz.log.SimpleLogger;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.CryptoModule;
import keywhiz.service.crypto.SecretTransformer;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import keywhiz.utility.DSLContexts;
import org.jooq.DSLContext;
import org.jooq.impl.DefaultTransactionProvider;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.JooqHealthCheck.OnFailure.LOG_ONLY;
import static keywhiz.JooqHealthCheck.OnFailure.RETURN_UNHEALTHY;

public class ServiceModule extends AbstractModule {
  private final Environment environment;
  private final KeywhizConfig config;

  public ServiceModule(KeywhizConfig config, Environment environment) {
    this.config = checkNotNull(config);
    this.environment = checkNotNull(environment);
  }

  @Override protected void configure() {
    // Initialize the BouncyCastle security provider for cryptography support.
    BouncyCastle.require();

    bind(Clock.class).toInstance(Clock.systemUTC());

    install(new CookieModule(config.getCookieKey()));
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

  // AuditLog

  @Provides @Singleton AuditLog simpleLogger() {
    return new SimpleLogger();
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
    DSLContext dslContext = DSLContexts.databaseAgnostic(dataSource);
    org.jooq.Configuration configuration = dslContext.configuration();
    // Disable support for nested transactions via savepoints (required for MySQL)
    // See: https://groups.google.com/forum/#!topic/jooq-user/zG0U6CkxI5o
    configuration.set(new DefaultTransactionProvider(configuration.connectionProvider(), false));
    return dslContext;
  }

  @Provides @Singleton SecretController secretController(SecretTransformer transformer,
      ContentCryptographer cryptographer, SecretDAOFactory secretDAOFactory,
      AclDAOFactory aclDAOFactory) {
    return new SecretController(transformer, cryptographer, secretDAOFactory.readwrite(),
        aclDAOFactory.readwrite());
  }

  @Provides @Singleton
  @Readonly SecretController readonlySecretController(SecretTransformer transformer,
      ContentCryptographer cryptographer, SecretDAOFactory secretDAOFactory,
      AclDAOFactory aclDAOFactory) {
    return new SecretController(transformer, cryptographer, secretDAOFactory.readonly(),
        aclDAOFactory.readonly());
  }

  @Provides @Singleton
  @Readonly Authenticator<BasicCredentials, User> authenticator(KeywhizConfig config,
      @Readonly DSLContext jooqContext) {
    return config.getUserAuthenticatorFactory().build(jooqContext);
  }
}
