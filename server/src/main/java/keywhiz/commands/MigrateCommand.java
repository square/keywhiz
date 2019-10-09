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
package keywhiz.commands;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import javax.sql.DataSource;
import keywhiz.KeywhizConfig;
import net.sourceforge.argparse4j.inf.Namespace;
import org.flywaydb.core.Flyway;

public class MigrateCommand extends ConfiguredCommand<KeywhizConfig> {
  public MigrateCommand() {
    super("migrate", "Executes any pending migrations and initializes the DB if necessary.");
  }

  @Override protected void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
      KeywhizConfig config) throws Exception {
    DataSource dataSource = config.getDataSourceFactory()
        .build(new MetricRegistry(), "migration-datasource");

    Flyway flyway = Flyway.configure().dataSource(dataSource).locations(config.getMigrationsDir()).table(config.getFlywaySchemaTable()).load();
    flyway.migrate();
  }
}
