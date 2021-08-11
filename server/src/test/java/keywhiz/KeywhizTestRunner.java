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

import com.google.inject.Injector;
import io.dropwizard.logging.BootstrapLogging;
import keywhiz.inject.InjectorFactory;
import keywhiz.jooq.tables.Accessgrants;
import keywhiz.jooq.tables.Clients;
import keywhiz.jooq.tables.Groups;
import keywhiz.jooq.tables.Memberships;
import keywhiz.jooq.tables.Secrets;
import keywhiz.jooq.tables.SecretsContent;
import keywhiz.jooq.tables.Users;
import keywhiz.test.KeywhizTests;
import keywhiz.test.ServiceContext;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.mockito.MockitoAnnotations;

/** Injecting test runner. */
public class KeywhizTestRunner extends BlockJUnit4ClassRunner {
  public KeywhizTestRunner(Class<?> klass) throws InitializationError {
    super(klass);
    BootstrapLogging.bootstrap();
  }

  private static final Injector injector = KeywhizTests.createInjector();

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

    Object test = getTestClass().getJavaClass().getDeclaredConstructor().newInstance();
    injector.injectMembers(test);
    MockitoAnnotations.initMocks(test);
    return test;
  }
}
