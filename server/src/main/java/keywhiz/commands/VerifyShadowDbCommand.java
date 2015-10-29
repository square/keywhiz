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
import com.google.common.collect.Sets;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import javax.annotation.Nullable;
import javax.sql.DataSource;
import keywhiz.KeywhizConfig;
import keywhiz.utility.DSLContexts;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.TableField;
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

public class VerifyShadowDbCommand extends ConfiguredCommand<KeywhizConfig> {
  private static final Logger logger = LoggerFactory.getLogger(VerifyShadowDbCommand.class);

  public VerifyShadowDbCommand() {
    super("verifyShadowDb", "verifies that all data in the current database exists in the shadow database and vice-versa.");
  }

  @Override protected void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
      KeywhizConfig config) throws Exception {
    DataSource dataSource =
        config.getDataSourceFactory().build(new MetricRegistry(), "sync-src-datasource");
    DSLContext dslSourceContext = DSLContexts.databaseAgnostic(dataSource);

    dataSource = config.getShadowWriteDataSourceFactory()
        .build(new MetricRegistry(), "sync-dest-datasource");
    DSLContext dslDestContext = DSLContexts.databaseAgnostic(dataSource);

    verify(dslSourceContext, dslDestContext);
  }

  protected static boolean verify(DSLContext dslSourceContext, DSLContext dslDestContext) {
    // Verify clients
    boolean ok = true;

    logger.info("Verifying clients");
    boolean r = verifyGeneric(dslSourceContext, CLIENTS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Clients.CLIENTS, CLIENTS.ID, null);
    logger.info(r ? "pass" : "fail");
    ok = ok && r;

    // Verify groups
    logger.info("Verifying groups");
    r = verifyGeneric(dslSourceContext, GROUPS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Groups.GROUPS, GROUPS.ID, null);
    logger.info(r ? "pass" : "fail");
    ok = ok && r;

    // Verify secrets
    logger.info("Verifying secrets");
    r = verifyGeneric(dslSourceContext, SECRETS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Secrets.SECRETS, SECRETS.ID, null);
    logger.info(r ? "pass" : "fail");
    ok = ok && r;

    // Verify secrets_content
    logger.info("Verifying secrets_content");
    r = verifyGeneric(dslSourceContext, SECRETS_CONTENT, dslDestContext,
        keywhiz.shadow_write.jooq.tables.SecretsContent.SECRETS_CONTENT, SECRETS_CONTENT.ID,
        SECRETS_CONTENT.VERSION);
    logger.info(r ? "pass" : "fail");
    ok = ok && r;

    // Verify accessgrants
    logger.info("Verifying accessgrants");
    r = verifyGeneric(dslSourceContext, ACCESSGRANTS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Accessgrants.ACCESSGRANTS, ACCESSGRANTS.ID, null);
    logger.info(r ? "pass" : "fail");
    ok = ok && r;

    // Verify memberships
    logger.info("Verifying memberships");
    r = verifyGeneric(dslSourceContext, MEMBERSHIPS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Memberships.MEMBERSHIPS, MEMBERSHIPS.ID, null);
    logger.info(r ? "pass" : "fail");
    ok = ok && r;

    // Verify users
    logger.info("Verifying users");
    r = verifyGeneric(dslSourceContext, USERS, dslDestContext,
        keywhiz.shadow_write.jooq.tables.Users.USERS, USERS.USERNAME, null);
    logger.info(r ? "pass" : "fail");
    ok = ok && r;

    return ok;
  }

  /**
   * Generic code to sync from one table to the other.
   */
  private static <R1 extends Record, R2 extends Record, X1, X2> boolean verifyGeneric(
      DSLContext dslSourceContext, Table<R1> sourceTable, DSLContext dslDestContext,
      Table<R2> destTable, TableField<R1, X1> f1, TableField<R1, X2> f2) {
    Result<R1> result1;
    Result<R2> result2;
    if (f2 == null) {
      result1 = dslSourceContext.selectFrom(sourceTable).orderBy(f1).fetch();
      result2 = dslDestContext.selectFrom(destTable).orderBy(f1).fetch();
    } else {
      result1 = dslSourceContext.selectFrom(sourceTable).orderBy(f1, f2).fetch();
      result2 = dslDestContext.selectFrom(destTable).orderBy(f1).fetch();
    }

    if (result1.size() != result2.size()) {
      logger.info("query returned a different number of rows: %d != %d", result1.size(),
          result2.size());
      Set<String> s1 = resultToSet(result1, f1, f2);
      Set<String> s2 = resultToSet(result2, f1, f2);
      logger.info(format("difference: %s", Sets.difference(s1, s2)));
      return false;
    }

    Field[] fields = result1.fields();
    boolean ok = true;
    for (int i=0; i<result1.size(); i++) {
      Record row1 = result1.get(i);
      Record row2 = result2.get(i);

      for (int j=0; j<fields.length; j++) {
        if (!compare(row1.getValue(fields[j]), row2.getValue(fields[j]))) {
          logger.info(format("mismatch at %d:", i));
          logger.info(Arrays.toString(fields));
          logger.info(row1.toString());
          logger.info(row2.toString());
          logger.info(format("field %s don't match", fields[j].getName()));
          ok = false;
        }
      }
    }
    return ok;
  }

  private static boolean compare(Object v1, Object v2) {
    if ((v1 == null) && (v2 == null)) {
      return true;
    }
    if ((v1 == null) || (v2 == null)) {
      return false;
    }
    if ((v1 instanceof OffsetDateTime) && (v2 instanceof OffsetDateTime)) {
      OffsetDateTime d1 = (OffsetDateTime)v1;
      OffsetDateTime d2 = (OffsetDateTime)v2;
      if (Math.abs(d1.toEpochSecond() - d2.toEpochSecond()) <= 1) {
        // allow +/- 1 seconds to take rounding/microseconds into account
        return true;
      }
    }
    return v1.equals(v2);
  }

  private static <R extends Record> Set<String> resultToSet(Result<R> result, Field f1, @Nullable Field f2) {
    Set<String> s = Sets.newHashSet();
    for (Record row : result) {
      if (f2 == null) {
        s.add(row.getValue(f1).toString());
      } else {
        s.add(row.getValue(f1).toString() + row.getValue(f2).toString());
      }
    }
    return s;
  }
}
