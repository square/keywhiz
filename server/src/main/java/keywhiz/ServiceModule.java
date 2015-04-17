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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.codahale.metrics.jdbi.InstrumentedTimingCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.Configuration;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.java8.auth.Authenticator;
import io.dropwizard.java8.jdbi.DBIFactory;
import io.dropwizard.java8.jdbi.OptionalContainerFactory;
import io.dropwizard.java8.jdbi.args.LocalDateTimeArgumentFactory;
import io.dropwizard.java8.jdbi.args.LocalDateTimeMapper;
import io.dropwizard.java8.jdbi.args.OptionalArgumentFactory;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.NamePrependingStatementRewriter;
import io.dropwizard.jdbi.args.JodaDateTimeArgumentFactory;
import io.dropwizard.jdbi.args.JodaDateTimeMapper;
import io.dropwizard.jdbi.logging.LogbackLog;
import io.dropwizard.setup.Environment;
import java.sql.SQLException;
import java.time.Clock;
import keywhiz.auth.BouncyCastle;
import keywhiz.auth.User;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.auth.cookie.CookieModule;
import keywhiz.auth.cookie.SessionCookie;
import keywhiz.auth.xsrf.Xsrf;
import keywhiz.generators.SecretGeneratorBindingModule;
import keywhiz.generators.TemplatedSecretGenerator;
import keywhiz.jdbi.SanerNamingStrategy;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.CryptoModule;
import keywhiz.service.crypto.SecretTransformer;
import keywhiz.service.daos.AclDeps;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.GroupDAO;
import keywhiz.service.daos.MapArgumentFactory;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretSeriesDAO;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.skife.jdbi.v2.ColonPrefixNamedParamStatementRewriter;
import org.skife.jdbi.v2.DBI;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class ServiceModule extends AbstractModule {
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ServiceModule.class);
  private static final Logger DBI_LOGGER = (Logger) LoggerFactory.getLogger(DBI.class);

  private final Environment environment;
  private final KeywhizConfig config;

  public ServiceModule(KeywhizConfig config, Environment environment) {
    this.config = checkNotNull(config);
    this.environment = checkNotNull(environment);
  }

  @Override protected void configure() {
    // Initialize the BouncyCastle security provider for BKS and cryptography support.
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

    install(new SecretGeneratorBindingModule() {
      @Override protected void configure() {
        bindSecretGenerator("templated", TemplatedSecretGenerator.class);
      }
    });
  }

  // ManagedDataSource

  @Provides @Singleton ManagedDataSource dataSource(Environment environment,
      KeywhizConfig config) {
    DataSourceFactory dataSourceFactory = config.getDataSourceFactory();
    ManagedDataSource dataSource = dataSourceFactory.build(environment.metrics(), "postgres-writable");
    environment.lifecycle().manage(dataSource);

    return dataSource;
  }

  @Provides @Singleton @Readonly ManagedDataSource readonlyDataSource(Environment environment,
      KeywhizConfig config) {
    DataSourceFactory dataSourceFactory = config.getDataSourceFactory();
    return dataSourceFactory.build(environment.metrics(), "postgres-readonly");
  }

  // jOOQ

  @Provides @Singleton DSLContext jooqContext(ManagedDataSource dataSource) throws SQLException {
    return DSL.using(dataSource.getConnection());
  }

  @Provides @Singleton @Readonly DSLContext readonlyJooqContext(@Readonly ManagedDataSource dataSource)
      throws SQLException {
    return DSL.using(dataSource.getConnection());
  }

  // DBI

  @Provides ObjectMapper configuredObjectMapper(Environment environment) {
    return environment.getObjectMapper();
  }

  @Provides @Singleton DBIFactory dbiFactory() {
    return new DBIFactory();
  }

  /**
   * Super lame copy of functionality from {@link DBIFactory}. Want both DBI instances to perform
   * similarly, however do NOT want a health check of the writable DBI. Failure should allow host
   * to remain in rotation of healthy servers since readonly DBI sufficient for critical tasks.
   */
  @Provides @Singleton DBI dbi(Environment environment, KeywhizConfig config,
      MapArgumentFactory mapArgumentFactory, ManagedDataSource dataSource) throws ClassNotFoundException {
    logger.debug("Creating DBI");

    DataSourceFactory dataSourceFactory = config.getDataSourceFactory();
    final DBI dbi = new DBI(dataSource);
    dbi.setSQLLog(new LogbackLog(DBI_LOGGER, Level.TRACE));
    dbi.setTimingCollector(new InstrumentedTimingCollector(environment.metrics(),
        new SanerNamingStrategy()));
    if (dataSourceFactory.isAutoCommentsEnabled()) {
      dbi.setStatementRewriter(new NamePrependingStatementRewriter(new ColonPrefixNamedParamStatementRewriter()));
    }

    dbi.registerArgumentFactory(mapArgumentFactory);
    dbi.registerArgumentFactory(
        new io.dropwizard.jdbi.args.OptionalArgumentFactory(dataSourceFactory.getDriverClass()));
    dbi.registerContainerFactory(new ImmutableListContainerFactory());
    dbi.registerContainerFactory(new ImmutableSetContainerFactory());
    dbi.registerContainerFactory(new io.dropwizard.jdbi.OptionalContainerFactory());
    dbi.registerArgumentFactory(new JodaDateTimeArgumentFactory());
    dbi.registerMapper(new JodaDateTimeMapper());
    dbi.registerArgumentFactory(new OptionalArgumentFactory(dataSourceFactory.getDriverClass()));
    dbi.registerContainerFactory(new OptionalContainerFactory());
    dbi.registerArgumentFactory(new LocalDateTimeArgumentFactory());
    dbi.registerMapper(new LocalDateTimeMapper());

    return dbi;
  }

  @Provides @Singleton
  @Readonly DBI readonlyDbi(DBIFactory factory, Environment environment, KeywhizConfig config,
      MapArgumentFactory mapArgumentFactory, @Readonly ManagedDataSource dataSource)
      throws ClassNotFoundException {
    logger.debug("Creating read-only DBI");
    DBI dbi = factory.build(environment, config.getReadonlyDataSourceFactory(), dataSource,
        "postgres-readonly");
    dbi.registerArgumentFactory(mapArgumentFactory);
    return dbi;
  }

  // DAOs using DBI

  @Provides @Singleton @Readonly SecretController readonlySecretController(
      SecretTransformer transformer, ContentCryptographer cryptographer,
      @Readonly SecretDAO secretDAO) {
    return new SecretController(transformer, cryptographer, secretDAO);
  }

  @Provides @Singleton SecretController secretController(SecretTransformer transformer,
      ContentCryptographer cryptographer, SecretDAO secretDAO) {
    return new SecretController(transformer, cryptographer, secretDAO);
  }

  @Provides @Singleton @Readonly GroupDAO readonlyGroupDAO(@Readonly DBI dbi) {
    return dbi.onDemand(GroupDAO.class);
  }

  @Provides @Singleton GroupDAO groupDAO(DBI dbi) {
    return dbi.onDemand(GroupDAO.class);
  }

  @Provides @Singleton @Readonly SecretDAO readonlySecretDAO(@Readonly DBI dbi) {
    return dbi.onDemand(SecretDAO.class);
  }

  @Provides @Singleton SecretDAO secretDAO(DBI dbi) {
    return dbi.onDemand(SecretDAO.class);
  }

  @Provides @Singleton @Readonly SecretSeriesDAO readonlySecretSeriesDAO(@Readonly DBI dbi) {
    return dbi.onDemand(SecretSeriesDAO.class);
  }

  @Provides @Singleton SecretSeriesDAO secretSeriesDAO(DBI dbi) {
    return dbi.onDemand(SecretSeriesDAO.class);
  }

  // DAOs using jOOQ

  @Provides @Singleton AclDAO aclDAO(DSLContext jooqContext, ClientDAO clientDAO, DBI dbi) {
    return new AclDAO(jooqContext, clientDAO, dbi.onDemand(AclDeps.class));
  }

  @Provides @Singleton
  @Readonly AclDAO readonlyAclDAO(@Readonly DSLContext jooqContext, @Readonly ClientDAO clientDAO, @Readonly DBI dbi) {
    return new AclDAO(jooqContext, clientDAO, dbi.onDemand(AclDeps.class));
  }

  @Provides @Singleton ClientDAO clientDAO(DSLContext jooqContext) {
    return new ClientDAO(jooqContext);
  }

  @Provides @Singleton @Readonly ClientDAO readonlyClientDAO(@Readonly DSLContext jooqContext) {
    return new ClientDAO(jooqContext);
  }

  @Provides @Singleton
  @Readonly Authenticator<BasicCredentials, User> authenticator(KeywhizConfig config,
      @Readonly DSLContext jooqContext) {
    return config.getUserAuthenticatorFactory().build(jooqContext);
  }
}
