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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import java.io.File;
import javax.sql.DataSource;
import javax.validation.Validation;
import javax.validation.Validator;
import keywhiz.commands.DbSeedCommand;
import keywhiz.utility.DSLContexts;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class MigrationsRule implements TestRule {
  @Override public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override public void evaluate() throws Throwable {
        File yamlFile = new File(Resources.getResource("keywhiz-test.yaml").getFile());
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ObjectMapper objectMapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
        KeywhizConfig config = new YamlConfigurationFactory<>(KeywhizConfig.class, validator, objectMapper, "dw")
            .build(yamlFile);

        DataSource dataSource = config.getDataSourceFactory()
            .build(new MetricRegistry(), "db-migrations");

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setLocations(config.getMigrationsDir());
        flyway.clean();
        flyway.migrate();

        DSLContext dslContext = DSLContexts.databaseAgnostic(dataSource);
        DbSeedCommand.doImport(dslContext);

        base.evaluate();
      }
    };
  }
}
