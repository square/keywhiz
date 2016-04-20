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
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import keywhiz.KeywhizConfig;
import keywhiz.service.daos.UserDAO;
import keywhiz.utility.DSLContexts;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jooq.DSLContext;

import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;

/**
 * Command to populate the database with development data.
 *
 * Only works if the environment is "development" to prevent accidental use against production
 * databases.
 *
 * Does not purge database, but writes data with specific IDs. Command should fail if run multiple
 * times (duplicate key violation).
 *
 * Uses jOOQ to insert data.
 *
 * Usage:
 * java -jar server/target/keywhiz-server-*-SNAPSHOT-shaded.jar db-seed server/src/main/resources/keywhiz-development.yaml
 */
public class DbSeedCommand extends ConfiguredCommand<KeywhizConfig> {
  // Didn't we say not to keep passwords in code? ;)
  public static final String defaultUser = "keywhizAdmin";
  public static final String defaultPassword = "adminPass";

  public DbSeedCommand() {
    super("db-seed", "Populates database with development data.");
  }

  @Override protected void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
      KeywhizConfig config) throws Exception {

    if (!config.getEnvironment().equals("development")) {
      throw new IllegalArgumentException("cannot call db-seed in non-development environment");
    }

    DataSource dataSource = config.getDataSourceFactory()
        .build(new MetricRegistry(), "db-seed-datasource");

    DSLContext dslContext = DSLContexts.databaseAgnostic(dataSource);
    doImport(dslContext);
  }

  /**
   * Inserts test data using dslContext.
   *
   * This method is exposed to the test framework (to leverage the same data for tests).
   *
   * @param dslContext jOOQ context
   */
  public static void doImport(DSLContext dslContext) {
    dslContext
        .insertInto(GROUPS, GROUPS.ID, GROUPS.NAME, GROUPS.CREATEDAT, GROUPS.UPDATEDAT)
        .values(916L, "Blackops", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(917L, "Security", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(918L, "Web", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(919L, "iOS", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(920L, "DeprecatedGroup", OffsetDateTime.parse("2013-03-12T11:23:43Z").toEpochSecond(), OffsetDateTime.parse("2013-03-12T11:23:43Z").toEpochSecond())
        .execute();

    dslContext
        .insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT, SECRETS.UPDATEDAT)
        .values(737L, "Nobody_PgPass", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond())
        .values(738L, "Hacking_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond())
        .values(739L, "Database_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond())
        .values(740L, "General_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond())
        .values(741L, "NonexistentOwner_Pass", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond())
        .values(742L, "Versioned_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond())
        .execute();

    dslContext
        .insertInto(SECRETS_CONTENT, SECRETS_CONTENT.ID, SECRETS_CONTENT.SECRETID, SECRETS_CONTENT.VERSION, SECRETS_CONTENT.CREATEDAT, SECRETS_CONTENT.UPDATEDAT, SECRETS_CONTENT.ENCRYPTED_CONTENT, SECRETS_CONTENT.METADATA)
        .values(937L, 737L, "", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:00:47Z").toEpochSecond(), "{\"derivationInfo\":\"Nobody_PgPass\",\"content\":\"5Eq97Y/6LMLUqH8rlXxEkOeMFmc3cYhQny0eotojNrF3DTFdQPyHVG5HeP5vzaFxqttcZkO56NvIwdD8k2xyIL5YRbCIA5MQ9LOnKN4tpnwb+Q\",\"iv\":\"jQAFJizi1MKZUcCxb6mTCA\"}", "{\"mode\":\"0400\",\"owner\":\"nobody\"}")
        .values(938L, 738L, "", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:01:59Z").toEpochSecond(), "{\"derivationInfo\":\"Hacking_Password\",\"content\":\"jpNVoXZao+b+f591w+CHWTj7D1M\",\"iv\":\"W+pT37jJP4uDGHmuczXVCA\"}", "")
        .values(939L, 739L, "", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), "{\"derivationInfo\":\"Database_Password\",\"content\":\"etQQFqMHQQpGr4aDlj5gDjiABkOb\",\"iv\":\"ia+YixjAEqp9W3JEjaYLvQ\"}", "")
        .values(940L, 740L, "", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), "{\"derivationInfo\":\"General_Password\",\"content\":\"A6kBLXwmx0EVtuIGTzxHiEZ/6yrXgg\",\"iv\":\"e4I0c3fog0TKqTAC2UxYtQ\"}", "")
        .values(941L, 741L, "", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), "{\"derivationInfo\":\"NonexistentOwner_Pass\",\"content\":\"+Pu1B5YgqGRIHzh17s5tPT3AYb+W\",\"iv\":\"ewRV3RhFfLnbWxY5pr401g\"}", "{\"owner\":\"NonExistant\",\"mode\":\"0400\"}")
        .values(942L, 742L, "0aae825a73e161d8", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
        .values(943L, 742L, "0aae825a73e161e8", OffsetDateTime.parse("2011-09-29T16:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T16:46:00Z").toEpochSecond(), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
        .values(944L, 742L, "0aae825a73e161f8", OffsetDateTime.parse("2011-09-29T17:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T17:46:00Z").toEpochSecond(), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
        .values(945L, 742L, "0aae825a73e161g8", OffsetDateTime.parse("2011-09-29T18:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T18:46:00Z").toEpochSecond(), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
        .execute();

    dslContext
        .insertInto(CLIENTS, CLIENTS.ID, CLIENTS.NAME, CLIENTS.CREATEDAT, CLIENTS.UPDATEDAT, CLIENTS.ENABLED, CLIENTS.AUTOMATIONALLOWED)
        .values(768L, "client", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, true)
        .values(769L, "CN=User1", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, false)
        .values(770L, "CN=User2", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(),  OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, false)
        .values(771L, "CN=User3", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, false)
        .values(772L, "CN=User4", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, false)
        .execute();

    dslContext
        .insertInto(ACCESSGRANTS, ACCESSGRANTS.ID, ACCESSGRANTS.GROUPID, ACCESSGRANTS.SECRETID, ACCESSGRANTS.CREATEDAT, ACCESSGRANTS.UPDATEDAT)
        .values(617L, 918L, 737L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(618L, 917L, 737L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(619L, 916L, 738L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(620L, 918L, 739L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(621L, 917L, 739L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(622L, 918L, 740L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(623L, 919L, 740L, OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond())
        .values(624L, 916L, 740L, OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(),  OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond())
        .values(625L, 917L, 740L, OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond())
        .values(626L, 918L, 741L, OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond())
        .values(627L, 917L, 741L, OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond())
        .execute();

    dslContext
        .insertInto(MEMBERSHIPS, MEMBERSHIPS.ID, MEMBERSHIPS.GROUPID, MEMBERSHIPS.CLIENTID, MEMBERSHIPS.CREATEDAT, MEMBERSHIPS.UPDATEDAT)
        .values(659L, 917L, 768L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(660L, 918L, 769L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(661L, 916L, 769L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(662L, 917L, 769L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(663L, 919L, 770L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(664L, 917L, 770L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(665L, 918L, 771L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(666L, 919L, 771L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(667L, 918L, 772L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .values(668L, 917L, 772L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond())
        .execute();

    new UserDAO(dslContext).createUserAt(
      defaultUser, defaultPassword,
      OffsetDateTime.parse("2012-06-22T14:38:09Z"),
      OffsetDateTime.parse("2012-06-22T14:38:09Z"));
  }
}
