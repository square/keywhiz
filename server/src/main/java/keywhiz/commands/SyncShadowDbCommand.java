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
import keywhiz.utility.DSLContexts;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertSetMoreStep;
import org.jooq.InsertSetStep;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static keywhiz.jooq.tables.Users.USERS;

public class SyncShadowDbCommand extends ConfiguredCommand<KeywhizConfig> {
  private static final Logger logger = LoggerFactory.getLogger(SyncShadowDbCommand.class);

  public SyncShadowDbCommand() {
    super("syncShadowDb", "Writes data from the current database to the shadow database.");
  }

  @Override protected void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
      KeywhizConfig config) throws Exception {
    DataSource dataSource =
        config.getDataSourceFactory().build(new MetricRegistry(), "sync-src-datasource");
    DSLContext dslSourceContext = DSLContexts.databaseAgnostic(dataSource);

    dataSource = config.getShadowWriteDataSourceFactory()
        .build(new MetricRegistry(), "sync-dest-datasource");
    DSLContext dslDestContext = DSLContexts.databaseAgnostic(dataSource);

    sync(dslSourceContext, dslDestContext);
  }

  protected static int sync(DSLContext dslSourceContext, DSLContext dslDestContext) {
    int rows = 0;
    // Synchronize clients
    logger.info("Synchronizing clients");
    int r = syncGeneric(dslSourceContext, CLIENTS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Clients.CLIENTS);
    logger.info(format("wrote %d rows", r));
    rows += r;

    // Synchronize groups
    logger.info("Sychronizing groups");
    r = syncGeneric(dslSourceContext, GROUPS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Groups.GROUPS);
    logger.info(format("wrote %d rows", r));
    rows += r;

    // Synchronize secrets
    logger.info("Synchronizing secrets");
    r = syncGeneric(dslSourceContext, SECRETS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Secrets.SECRETS);
    logger.info(format("wrote %d rows", r));
    rows += r;

    // Synchronize secrets_content (references secrets)
    logger.info("Synchronizing secrets_content");
    r = syncGeneric(dslSourceContext, SECRETS_CONTENT, dslDestContext,
        keywhiz.shadow_write.jooq.tables.SecretsContent.SECRETS_CONTENT);
    logger.info(format("wrote %d rows", r));
    rows += r;

    // Synchronize accessgrants (references secrets + groups)
    logger.info("Synchronizing accessgrants");
    r = syncGeneric(dslSourceContext, ACCESSGRANTS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Accessgrants.ACCESSGRANTS);
    logger.info(format("wrote %d rows", r));
    rows += r;

    // Synchronize memberships (references clients + groups)
    logger.info("Synchronizing memberships");
    r = syncGeneric(dslSourceContext, MEMBERSHIPS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Memberships.MEMBERSHIPS);
    logger.info(format("wrote %d rows", r));
    rows += r;

    // Synchronize users
    logger.info("Synchronizing users");
    r = syncGeneric(dslSourceContext, USERS, dslDestContext, keywhiz.shadow_write.jooq.tables.Users.USERS);
    logger.info(format("wrote %d rows", r));
    rows += r;
    return rows;
  }

  /**
   * Generic code to sync from one table to the other.
   */
  private static <R extends Record> int syncGeneric(DSLContext dslSourceContext, Table<R> sourceTable,
      DSLContext dslDestContext, Table destTable) {
    Result<R> result = dslSourceContext.selectFrom(sourceTable).fetch();
    Field[] fields = result.fields();

    int rows = 0;

    for (Record row : result) {
      InsertSetStep insert = dslDestContext.insertInto(destTable);
      InsertSetMoreStep insert2 = null;
      for (int i = 0; i < fields.length; i++) {
        if (i == 0) {
          insert2 = insert.set(fields[i], row.getValue(fields[i]));
        } else {
          insert2 = insert2.set(fields[i], row.getValue(fields[i]));
        }
      }
      rows += insert2.onDuplicateKeyIgnore().execute();
    }
    return rows;
  }
}
