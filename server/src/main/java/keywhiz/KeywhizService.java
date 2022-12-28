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

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import javax.sql.DataSource;
import keywhiz.auth.mutualssl.ClientCertificateFilter;
import keywhiz.commands.AddUserCommand;
import keywhiz.commands.BackfillOwnershipCommand;
import keywhiz.commands.DbSeedCommand;
import keywhiz.commands.DropDeletedSecretsCommand;
import keywhiz.commands.GenerateAesKeyCommand;
import keywhiz.commands.MigrateCommand;
import keywhiz.commands.PreviewMigrateCommand;
import keywhiz.inject.InjectorFactory;
import keywhiz.service.filters.CookieRenewingFilter;
import keywhiz.service.filters.SecurityHeadersFilter;
import keywhiz.service.permissions.PermissionCheck;
import keywhiz.service.providers.AuthResolver;
import keywhiz.service.providers.AutomationClientAuthFactory;
import keywhiz.service.providers.ClientAuthFactory;
import keywhiz.service.providers.UserAuthFactory;
import keywhiz.service.resources.BatchSecretDeliveryResource;
import keywhiz.service.resources.SecretDeliveryResource;
import keywhiz.service.resources.SecretsDeliveryResource;
import keywhiz.service.resources.StatusResource;
import keywhiz.service.resources.admin.ClientsResource;
import keywhiz.service.resources.admin.GroupsResource;
import keywhiz.service.resources.admin.MembershipResource;
import keywhiz.service.resources.admin.SecretsResource;
import keywhiz.service.resources.admin.SessionLoginResource;
import keywhiz.service.resources.admin.SessionLogoutResource;
import keywhiz.service.resources.admin.SessionMeResource;
import keywhiz.service.resources.automation.AutomationClientResource;
import keywhiz.service.resources.automation.AutomationEnrollClientGroupResource;
import keywhiz.service.resources.automation.AutomationGroupResource;
import keywhiz.service.resources.automation.AutomationSecretAccessResource;
import keywhiz.service.resources.automation.AutomationSecretResource;
import keywhiz.service.resources.automation.v2.BackfillRowHmacResource;
import keywhiz.service.resources.automation.v2.BackupResource;
import keywhiz.service.resources.automation.v2.ClientResource;
import keywhiz.service.resources.automation.v2.GroupResource;
import keywhiz.service.resources.automation.v2.SecretResource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starting point for Keywhiz, an implementation of the Dropwizard Service.
 */
public class KeywhizService extends Application<KeywhizConfig> {
  /**
   * Entry point into application.
   * @param args passed onto {@link #run(String[])}
   * @throws Exception for any uncaught application exception
   */
  public static void main(String[] args) throws Exception {
    new KeywhizService().run(args);
  }

  private static final Logger logger = LoggerFactory.getLogger(KeywhizService.class);

  private Injector injector;

  @SuppressWarnings("unused")
  public Injector getInjector() {
    return injector;
  }

  public void setInjector(Injector injector) {
    this.injector = injector;
  }

  @Override public String getName() {
    return "keywhiz";
  }

  @Override public void initialize(Bootstrap<KeywhizConfig> bootstrap) {
    customizeObjectMapper(bootstrap.getObjectMapper());

    logger.debug("Registering commands");
    bootstrap.addCommand(new PreviewMigrateCommand());
    bootstrap.addCommand(new MigrateCommand());
    bootstrap.addCommand(new DbSeedCommand());
    bootstrap.addCommand(new GenerateAesKeyCommand());
    bootstrap.addCommand(new AddUserCommand());
    bootstrap.addCommand(new DropDeletedSecretsCommand());
    bootstrap.addCommand(new BackfillOwnershipCommand());
  }

  @SuppressWarnings("unchecked")
  @Override public void run(KeywhizConfig config, Environment environment) throws Exception {
    try {
      doRun(config, environment);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw e;
    }
  }

  private void doRun(KeywhizConfig config, Environment environment) {
    ensureInjectorCreated(config, environment);

    JerseyEnvironment jersey = environment.jersey();

    logger.debug("Registering resource filters");
    jersey.register(injector.getInstance(ClientCertificateFilter.class));

    logger.debug("Registering servlet filters");
    environment.servlets().addFilter("security-headers-filter", injector.getInstance(SecurityHeadersFilter.class))
        .addMappingForUrlPatterns(null, /* Default is for requests */
            false /* Can be after other filters */, "/*" /* Every request */);
    jersey.register(injector.getInstance(CookieRenewingFilter.class));

    logger.debug("Registering providers");
    jersey.register(new AuthResolver.Binder(injector.getInstance(ClientAuthFactory.class),
        injector.getInstance(AutomationClientAuthFactory.class),
        injector.getInstance(UserAuthFactory.class)));

    jersey.register(injector.getInstance(PermissionCheck.class));

    logger.debug("Registering resources");
    jersey.register(injector.getInstance(BackfillRowHmacResource.class));
    jersey.register(injector.getInstance(ClientResource.class));
    jersey.register(injector.getInstance(ClientsResource.class));
    jersey.register(injector.getInstance(GroupResource.class));
    jersey.register(injector.getInstance(GroupsResource.class));
    jersey.register(injector.getInstance(MembershipResource.class));
    jersey.register(injector.getInstance(SecretsDeliveryResource.class));
    jersey.register(injector.getInstance(SecretResource.class));
    jersey.register(injector.getInstance(SecretsResource.class));
    jersey.register(injector.getInstance(SecretDeliveryResource.class));
    jersey.register(injector.getInstance(SessionLoginResource.class));
    jersey.register(injector.getInstance(SessionLogoutResource.class));
    jersey.register(injector.getInstance(SessionMeResource.class));
    jersey.register(injector.getInstance(AutomationClientResource.class));
    jersey.register(injector.getInstance(AutomationGroupResource.class));
    jersey.register(injector.getInstance(AutomationSecretResource.class));
    jersey.register(injector.getInstance(AutomationEnrollClientGroupResource.class));
    jersey.register(injector.getInstance(AutomationSecretAccessResource.class));
    jersey.register(injector.getInstance(StatusResource.class));
    jersey.register(injector.getInstance(BackupResource.class));
    jersey.register(injector.getInstance(BatchSecretDeliveryResource.class));

    ManualStatusHealthCheck mshc = new ManualStatusHealthCheck();
    environment.healthChecks().register("manualStatus", mshc);
    environment.admin().addServlet("manualStatus", new ManualStatusServlet(mshc)).addMapping("/status/*");

    validateDatabase(config);

    logger.debug("Keywhiz configuration complete");
  }

  @VisibleForTesting
  void ensureInjectorCreated(KeywhizConfig config, Environment environment) {
    if (injector == null) {
      logger.debug("No existing guice injector; creating new one");
      injector = InjectorFactory.createInjector(config, environment);
    }
  }

  private void validateDatabase(KeywhizConfig config) {
    logger.debug("Validating database state");
    DataSource dataSource = config.getDataSourceFactory()
        .build(new MetricRegistry(), "flyway-validation-datasource");
    Flyway flyway = Flyway.configure().dataSource(dataSource).locations(config.getMigrationsDir()).table(config.getFlywaySchemaTable()).load();
    flyway.validate();
  }

  /**
   * Customizes ObjectMapper for common settings.
   *
   * @param objectMapper to be customized
   * @return customized input factory
   */
  public static ObjectMapper customizeObjectMapper(ObjectMapper objectMapper) {
    objectMapper.registerModules(new Jdk8Module());
    objectMapper.registerModules(new JavaTimeModule());
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper;
  }

  @Override
  public void onFatalError() {}
}
