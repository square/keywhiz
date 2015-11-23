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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.java8.Java8Bundle;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.util.Map;
import javax.sql.DataSource;
import keywhiz.auth.mutualssl.ClientCertificateFilter;
import keywhiz.auth.xsrf.XsrfServletFilter;
import keywhiz.commands.DbSeedCommand;
import keywhiz.commands.GenerateAesKeyCommand;
import keywhiz.commands.MigrateCommand;
import keywhiz.commands.PreviewMigrateCommand;
import keywhiz.generators.SecretGenerator;
import keywhiz.generators.SecretGeneratorFactory;
import keywhiz.generators.SecretGeneratorModule;
import keywhiz.service.filters.CookieRenewingFilter;
import keywhiz.service.filters.SecurityHeadersFilter;
import keywhiz.service.providers.AuthResolver;
import keywhiz.service.providers.AutomationClientAuthFactory;
import keywhiz.service.providers.ClientAuthFactory;
import keywhiz.service.providers.UserAuthFactory;
import keywhiz.service.resources.SecretDeliveryResource;
import keywhiz.service.resources.SecretsDeliveryResource;
import keywhiz.service.resources.admin.ClientsResource;
import keywhiz.service.resources.admin.GroupsResource;
import keywhiz.service.resources.admin.MembershipResource;
import keywhiz.service.resources.admin.SecretGeneratorsResource;
import keywhiz.service.resources.admin.SecretsResource;
import keywhiz.service.resources.admin.SessionLoginResource;
import keywhiz.service.resources.admin.SessionLogoutResource;
import keywhiz.service.resources.admin.SessionMeResource;
import keywhiz.service.resources.automation.AutomationClientResource;
import keywhiz.service.resources.automation.AutomationEnrollClientGroupResource;
import keywhiz.service.resources.automation.AutomationGroupResource;
import keywhiz.service.resources.automation.AutomationSecretAccessResource;
import keywhiz.service.resources.automation.AutomationSecretGeneratorsResource;
import keywhiz.service.resources.automation.AutomationSecretResource;
import keywhiz.service.resources.automation.v2.ClientResource;
import keywhiz.service.resources.automation.v2.GroupResource;
import keywhiz.service.resources.automation.v2.SecretResource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.utility.SecretTemplateCompiler.validName;

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

  private final Map<String, SecretGeneratorFactory<?>> secretGeneratorFactories = Maps.newHashMap();
  private final Map<String, SecretGenerator<?>> secretGenerators = Maps.newHashMap();
  private Injector injector;

  @SuppressWarnings("unused")
  public Injector getInjector() {
    return injector;
  }

  public void setInjector(Injector injector) {
    this.injector = injector;
  }

  /**
   * Register a {@link SecretGeneratorFactory} and its name.
   *
   * @param name associated with generators from factory.
   * @param secretGeneratorFactory factory to register.
   */
  @SuppressWarnings("unused")
  public void addSecretGeneratorFactory(String name, SecretGeneratorFactory<?> secretGeneratorFactory) {
    checkArgument(validName(name)
        && !secretGenerators.containsKey(name));
    logger.debug("Registering SecretGeneratorFactory {} -> {}",
        name, secretGeneratorFactory.getClass().getSimpleName());
    secretGeneratorFactories.put(name, checkNotNull(secretGeneratorFactory));
  }

  /**
   * Register a {@link SecretGenerator} and its name.
   *
   * @param name associated with generator.
   * @param secretGenerator generator to register.
   */
  @SuppressWarnings("unused")
  public void addSecretGenerator(String name, SecretGenerator<?> secretGenerator) {
    checkArgument(validName(name)
        && !secretGeneratorFactories.containsKey(name));
    logger.debug("Registering SecretGenerator {} -> {}",
        name, secretGenerator.getClass().getSimpleName());
    secretGenerators.put(name, checkNotNull(secretGenerator));
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

    logger.debug("Registering bundles");
    bootstrap.addBundle(new Java8Bundle());
    bootstrap.addBundle(new NamedAssetsBundle());
    bootstrap.addBundle(new UiAssetsBundle());
  }

  @SuppressWarnings("unchecked")
  @Override public void run(KeywhizConfig config, Environment environment) throws Exception {
    if (injector == null) {
      logger.debug("No existing guice injector; creating new one");
      injector = Guice.createInjector(
          new ServiceModule(config, environment),
          new SecretGeneratorModule(secretGeneratorFactories, secretGenerators));
    }

    JerseyEnvironment jersey = environment.jersey();

    logger.debug("Registering resource filters");
    jersey.register(injector.getInstance(ClientCertificateFilter.class));

    logger.debug("Registering servlet filters");
    environment.servlets().addFilter("security-headers-filter", injector.getInstance(SecurityHeadersFilter.class))
        .addMappingForUrlPatterns(null, /* Default is for requests */
            false /* Can be after other filters */, "/*" /* Every request */);
    jersey.register(injector.getInstance(CookieRenewingFilter.class));

    environment.servlets().addFilter("xsrf-filter", injector.getInstance(XsrfServletFilter.class))
        .addMappingForUrlPatterns(null /* Default is for requests */, false /* Can be after other filters */,
            "/admin/*" /* Path to filter on */);

    logger.debug("Registering providers");
    jersey.register(new AuthResolver.Binder(injector.getInstance(ClientAuthFactory.class),
        injector.getInstance(AutomationClientAuthFactory.class),
        injector.getInstance(UserAuthFactory.class)));

    logger.debug("Registering resources");
    jersey.register(injector.getInstance(ClientResource.class));
    jersey.register(injector.getInstance(ClientsResource.class));
    jersey.register(injector.getInstance(GroupResource.class));
    jersey.register(injector.getInstance(GroupsResource.class));
    jersey.register(injector.getInstance(MembershipResource.class));
    jersey.register(injector.getInstance(SecretsDeliveryResource.class));
    jersey.register(injector.getInstance(SecretResource.class));
    jersey.register(injector.getInstance(SecretsResource.class));
    jersey.register(injector.getInstance(SecretGeneratorsResource.class));
    jersey.register(injector.getInstance(SecretDeliveryResource.class));
    jersey.register(injector.getInstance(SessionLoginResource.class));
    jersey.register(injector.getInstance(SessionLogoutResource.class));
    jersey.register(injector.getInstance(SessionMeResource.class));
    jersey.register(injector.getInstance(AutomationClientResource.class));
    jersey.register(injector.getInstance(AutomationGroupResource.class));
    jersey.register(injector.getInstance(AutomationSecretResource.class));
    jersey.register(injector.getInstance(AutomationEnrollClientGroupResource.class));
    jersey.register(injector.getInstance(AutomationSecretAccessResource.class));
    jersey.register(injector.getInstance(AutomationSecretGeneratorsResource.class));
    logger.debug("Keywhiz configuration complete");

    validateDabase(config);
  }

  private void validateDabase(KeywhizConfig config) {
    logger.debug("Validating database state");
    DataSource dataSource = config.getDataSourceFactory()
        .build(new MetricRegistry(), "flyway-validation-datasource");
    Flyway flyway = new Flyway();
    flyway.setDataSource(dataSource);
    flyway.setLocations(config.getMigrationsDir());
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
    return objectMapper;
  }
}
