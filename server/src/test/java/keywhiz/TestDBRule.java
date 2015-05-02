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
import com.google.common.io.Resources;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import java.io.File;
import java.io.IOException;
import javax.validation.Validation;
import javax.validation.Validator;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.rules.ExternalResource;

import static com.google.common.base.Preconditions.checkState;

/**
 * Compatible with JUnit {@link org.junit.Rule} or {@link org.junit.ClassRule}, this class provides
 * a DBI instance for an H2 in-memory database. The database is migrated at startup and
 * automatically closed down.
 */
public class TestDBRule extends ExternalResource {
  private DSLContext dslContext;
  private JdbcConnectionPool dataSource;

  @Override public void before() throws Throwable {
    super.before();
    dataSource = JdbcConnectionPool.create("jdbc:h2:mem:test", "", "");

    File yamlFile = new File(Resources.getResource("keywhiz-test.yaml").getFile());
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    ObjectMapper objectMapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
    KeywhizConfig config;
    try {
      config = new ConfigurationFactory<>(KeywhizConfig.class, validator, objectMapper, "dw")
          .build(yamlFile);
    } catch (IOException | ConfigurationException e) {
      throw new AssertionError(e);
    }

    Flyway flyway = new Flyway();
    flyway.setDataSource(dataSource);
    // sql stuff for h2:
    flyway.setLocations("db/postgres/migration");
    flyway.migrate();

    dslContext = DSL.using(dataSource, SQLDialect.H2,
        new Settings()
            .withRenderSchema(false)
            .withRenderNameStyle(RenderNameStyle.AS_IS));
  }

  @Override public void after() {
    dataSource.dispose();
    super.after();
  }

  public DSLContext jooqContext() {
    checkState(dslContext != null, "JOOQ DSLContext not yet initialized.");
    return dslContext;
  }
}
