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
import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.logging.LoggingFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.io.File;
import java.io.IOException;
import javax.validation.Validation;
import javax.validation.Validator;
import keywhiz.jooq.tables.Accessgrants;
import keywhiz.jooq.tables.Clients;
import keywhiz.jooq.tables.Groups;
import keywhiz.jooq.tables.Memberships;
import keywhiz.jooq.tables.Secrets;
import keywhiz.jooq.tables.SecretsContent;
import keywhiz.jooq.tables.Users;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.mockito.MockitoAnnotations;

/** Injecting test runner. */
public class KeywhizTestRunner extends BlockJUnit4ClassRunner {
  public KeywhizTestRunner(Class<?> klass) throws InitializationError {
    super(klass);
    LoggingFactory.bootstrap();
  }

  private static final Injector injector = createInjector();

  static Injector createInjector() {
    KeywhizService service = new KeywhizService();
    Bootstrap<KeywhizConfig> bootstrap = new Bootstrap<>(service);
    service.initialize(bootstrap);

    File yamlFile = new File(Resources.getResource("keywhiz-test.yaml").getFile());
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    ObjectMapper objectMapper = bootstrap.getObjectMapper().copy();
    KeywhizConfig config;
    try {
      config = new ConfigurationFactory<>(KeywhizConfig.class, validator, objectMapper, "dw")
          .build(yamlFile);
    } catch (IOException | ConfigurationException e) {
      throw Throwables.propagate(e);
    }

    Environment environment = new Environment(service.getName(),
        objectMapper,
        validator,
        bootstrap.getMetricRegistry(),
        bootstrap.getClassLoader());

    Injector injector = Guice.createInjector(new ServiceModule(config, environment));

    service.setInjector(injector);
    return injector;
  }

  @Override protected Object createTest() throws Exception {
    // Reset database. Sometimes, the truncate command fails. I don't know why?
    DSLContext jooqContext = injector.getInstance(DSLContext.class);
    try {
      jooqContext.truncate(Users.USERS).execute();
    } catch(DataAccessException e) {}
    try {
      jooqContext.truncate(SecretsContent.SECRETS_CONTENT).execute();
    } catch(DataAccessException e) {}
    try {
      jooqContext.truncate(Memberships.MEMBERSHIPS).execute();
    } catch(DataAccessException e) {}
    try {
      jooqContext.truncate(Accessgrants.ACCESSGRANTS).execute();
    } catch(DataAccessException e) {}
    try {
      jooqContext.truncate(Clients.CLIENTS).execute();
    } catch(DataAccessException e) {}
    try {
      jooqContext.truncate(Groups.GROUPS).execute();
    } catch(DataAccessException e) {}
    try {
      jooqContext.truncate(Secrets.SECRETS).execute();
    } catch(DataAccessException e) {}

    Object object = injector.getInstance(getTestClass().getJavaClass());
    MockitoAnnotations.initMocks(object);
    return object;
  }
}
