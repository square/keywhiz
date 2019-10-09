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
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreviewMigrateCommand extends ConfiguredCommand<KeywhizConfig> {
  private static final Logger logger = LoggerFactory.getLogger(PreviewMigrateCommand.class);

  public PreviewMigrateCommand() {
    super("preview-migrate", "Displays information on pending migrations without running them.");
  }

  @Override protected void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
      KeywhizConfig config) throws Exception {
    DataSource dataSource = config.getDataSourceFactory()
        .build(new MetricRegistry(), "migration-preview-datasource");

    Flyway flyway = Flyway.configure().dataSource(dataSource).locations(config.getMigrationsDir()).table(config.getFlywaySchemaTable()).load();
    MigrationInfoService info = flyway.info();

    MigrationInfo current = info.current();
    if (current == null) {
      logger.info("No migrations have been run yet.");
    } else {
      logger.info("Currently applied migration:");
      logger.info("* {} - {}", current.getVersion(), current.getDescription());
    }

    if (info.pending().length > 0) {
      logger.info("Pending migrations:");
      for (MigrationInfo migration : info.pending()) {
        logger.info("* {} - {}", migration.getVersion(), migration.getDescription());
      }
    } else {
      logger.info("No pending migrations");
    }
  }
}
