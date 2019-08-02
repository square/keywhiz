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
        .insertInto(GROUPS, GROUPS.ID, GROUPS.NAME, GROUPS.CREATEDAT, GROUPS.UPDATEDAT, GROUPS.METADATA)
        .values(916L, "Blackops", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "{\"app\" : \"Blackops\"}")
        .values(917L, "Security", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "{\"app\" : \"Security\"}")
        .values(918L, "Web", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "{\"app\" : \"Web\"}")
        .values(919L, "iOS", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "{\"app\" : \"iOSDev\"}")
        .values(920L, "DeprecatedGroup", OffsetDateTime.parse("2013-03-12T11:23:43Z").toEpochSecond(), OffsetDateTime.parse("2013-03-12T11:23:43Z").toEpochSecond(), "{\"app\" : \"DeprecatedApp\"}")
        .execute();

    dslContext
        .insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT, SECRETS.UPDATEDAT, SECRETS.CURRENT)
        .values(737L, "Nobody_PgPass", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 937L)
        .values(738L, "Hacking_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 938L)
        .values(739L, "Database_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 939L)
        .values(740L, "General_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 940L)
        .values(741L, "NonexistentOwner_Pass", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 941L)
        .values(742L, "Versioned_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 942L)
        .values(743L, "Deleted_Secret", OffsetDateTime.parse("2016-06-08T02:03:04Z").toEpochSecond(), OffsetDateTime.parse("2016-06-08T02:03:04Z").toEpochSecond(), null)
        .execute();

    dslContext
        .insertInto(SECRETS_CONTENT, SECRETS_CONTENT.ID, SECRETS_CONTENT.SECRETID, SECRETS_CONTENT.CREATEDAT, SECRETS_CONTENT.UPDATEDAT, SECRETS_CONTENT.ENCRYPTED_CONTENT, SECRETS_CONTENT.METADATA)
        .values(937L, 737L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:00:47Z").toEpochSecond(), "{\"derivationInfo\":\"Nobody_PgPass\",\"content\":\"5Eq97Y/6LMLUqH8rlXxEkOeMFmc3cYhQny0eotojNrF3DTFdQPyHVG5HeP5vzaFxqttcZkO56NvIwdD8k2xyIL5YRbCIA5MQ9LOnKN4tpnwb+Q\",\"iv\":\"jQAFJizi1MKZUcCxb6mTCA\"}", "{\"mode\":\"0400\",\"owner\":\"nobody\"}")
        .values(938L, 738L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:01:59Z").toEpochSecond(), "{\"derivationInfo\":\"Hacking_Password\",\"content\":\"jpNVoXZao+b+f591w+CHWTj7D1M\",\"iv\":\"W+pT37jJP4uDGHmuczXVCA\"}", "")
        .values(939L, 739L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), "{\"derivationInfo\":\"Database_Password\",\"content\":\"etQQFqMHQQpGr4aDlj5gDjiABkOb\",\"iv\":\"ia+YixjAEqp9W3JEjaYLvQ\"}", "")
        .values(940L, 740L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), "{\"derivationInfo\":\"General_Password\",\"content\":\"A6kBLXwmx0EVtuIGTzxHiEZ/6yrXgg\",\"iv\":\"e4I0c3fog0TKqTAC2UxYtQ\"}", "")
        .values(941L, 741L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), "{\"derivationInfo\":\"NonexistentOwner_Pass\",\"content\":\"+Pu1B5YgqGRIHzh17s5tPT3AYb+W\",\"iv\":\"ewRV3RhFfLnbWxY5pr401g\"}", "{\"owner\":\"NonExistant\",\"mode\":\"0400\"}")
        .values(942L, 742L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
        .values(943L, 742L, OffsetDateTime.parse("2011-09-29T16:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T16:46:00Z").toEpochSecond(), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
        .values(944L, 742L, OffsetDateTime.parse("2011-09-29T17:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T17:46:00Z").toEpochSecond(), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
        .values(945L, 742L, OffsetDateTime.parse("2011-09-29T18:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T18:46:00Z").toEpochSecond(), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
        .values(946L, 743L, OffsetDateTime.parse("2016-06-08T02:03:04Z").toEpochSecond(), OffsetDateTime.parse("2016-06-08T02:03:04Z").toEpochSecond(), "{\"derivationInfo\":\"Deleted_Secret\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
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
        .insertInto(ACCESSGRANTS, ACCESSGRANTS.ID, ACCESSGRANTS.GROUPID, ACCESSGRANTS.SECRETID, ACCESSGRANTS.CREATEDAT, ACCESSGRANTS.UPDATEDAT, ACCESSGRANTS.VERIFICATION_HMAC)
        .values(617L, 918L, 737L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "D358C5E8D0725232236B485F39D72E63E69DB60AE20F1A4EE79ADF47BCD832D2")
        .values(618L, 917L, 737L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "377A5B3529936448966125EE223841480855BA1A5CB036587451913E3E00E14E")
        .values(619L, 916L, 738L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "")
        .values(620L, 918L, 739L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "0465AC479EC560AF8F509DBFE16F261C5729A4E1E601FCEFEE3EE5C85081B06A")
        .values(621L, 917L, 739L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "324960419274C5BA1E6D34608F79DB77E14C3FD992E7E2EDE1046EF7F3378C67")
        .values(622L, 918L, 740L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "96AB4A5D3F0AAE49C20B74D97DA6F449C1B2CC0BA6E310D2F7D16F99171A2DB3")
        .values(623L, 919L, 740L, OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), "BC8A3A3135AE846021356D55C838B5A9059317FB8687B574AB0D73D55AFB63E0")
        .values(624L, 916L, 740L, OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(),  OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), "")
        .values(625L, 917L, 740L, OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), "DE75E24FC8B984F0B4810E95420B5133FBD987CD3FDDD0D1CFCA9D700B986CBA")
        .values(626L, 918L, 741L, OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), "3690D42A6E3DFE038796163FE19EF8DF767243D50047FB01E11D031492D92F4D")
        .values(627L, 917L, 741L, OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), "941C0F2AB4F2C65065D5797062D5D79431F96A514916896BF7FB8AAC67B5A729")
        .execute();

    dslContext
        .insertInto(MEMBERSHIPS, MEMBERSHIPS.ID, MEMBERSHIPS.GROUPID, MEMBERSHIPS.CLIENTID, MEMBERSHIPS.CREATEDAT, MEMBERSHIPS.UPDATEDAT, MEMBERSHIPS.VERIFICATION_HMAC)
        .values(659L, 917L, 768L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "5E48218A6D6F6A52659B09D0900EB5BBBFB75C281A5F5C92B7292F506BA0866D")
        .values(660L, 918L, 769L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "")
        .values(661L, 916L, 769L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "")
        .values(662L, 917L, 769L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "")
        .values(663L, 919L, 770L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "844FDCD7B2D0E6DB18F8BF56D8FCDF65BA9E64B775BB047B8BDF73A715255FC8")
        .values(664L, 917L, 770L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "6E7923D31260EECEBB3BB4CAE0EF2ED3622D0B3E9FDFF062A541CDD8DF452D3A")
        .values(665L, 918L, 771L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "")
        .values(666L, 919L, 771L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "")
        .values(667L, 918L, 772L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "")
        .values(668L, 917L, 772L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), "")
        .execute();

    new UserDAO(dslContext).createUserAt(
      defaultUser, defaultPassword,
      OffsetDateTime.parse("2012-06-22T14:38:09Z"),
      OffsetDateTime.parse("2012-06-22T14:38:09Z"));
  }
}
