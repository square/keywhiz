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
import com.google.common.io.BaseEncoding;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import keywhiz.KeywhizConfig;
import keywhiz.service.daos.UserDAO;
import keywhiz.utility.DSLContexts;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jooq.DSLContext;

import static java.nio.charset.StandardCharsets.UTF_8;
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

  private static final String KEY_ALGORITHM = "AES";
  private static final byte[] KEY_BYTES = {
      18, -91, 116, -114, -50, 49, -35, -70, -120, -37, 0, -45, 104, 7, -114, -28, 97, 30, 83, 102,
      -61, 78, 92, -3, -63, 116, 101, -119, 40, 64, -87, 45
  };

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

  private static String computeHmac(byte[] data) {
    SecretKey hmacKey = new SecretKeySpec(KEY_BYTES, KEY_ALGORITHM);
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(hmacKey);
      return BaseEncoding.base16().encode(mac.doFinal(data));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      return null;
    }
  }

  public static String computeRowHmac(String table, List<Object> fields) {
    String joinedFields = fields.stream()
        .map(object -> Objects.toString(object, null))
        .collect(Collectors.joining("|"));
    String hmacContent = table + "|" + joinedFields;
    return computeHmac(hmacContent.getBytes(UTF_8));
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
        .insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT, SECRETS.UPDATEDAT, SECRETS.CURRENT, SECRETS.ROW_HMAC)
        .values(737L, "Nobody_PgPass", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 937L, computeRowHmac(SECRETS.getName(), List.of("Nobody_PgPass", 737L)))
        .values(738L, "Hacking_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 938L, computeRowHmac(SECRETS.getName(), List.of("Hacking_Password", 738L)))
        .values(739L, "Database_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 939L, computeRowHmac(SECRETS.getName(), List.of("Database_Password", 739L)))
        .values(740L, "General_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 940L, computeRowHmac(SECRETS.getName(), List.of("General_Password", 740L)))
        .values(741L, "NonexistentOwner_Pass", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 941L, computeRowHmac(SECRETS.getName(), List.of("NonexistentOwner_Pass", 741L)))
        .values(742L, "Versioned_Password", OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), 942L, computeRowHmac(SECRETS.getName(), List.of("Versioned_Password", 742L)))
        .values(743L, "Deleted_Secret", OffsetDateTime.parse("2016-06-08T02:03:04Z").toEpochSecond(), OffsetDateTime.parse("2016-06-08T02:03:04Z").toEpochSecond(), null, computeRowHmac(SECRETS.getName(), List.of("Deleted_Secret", 743L)))
        .execute();

    String encrypted937 = "{\"derivationInfo\":\"Nobody_PgPass\",\"content\":\"5Eq97Y/6LMLUqH8rlXxEkOeMFmc3cYhQny0eotojNrF3DTFdQPyHVG5HeP5vzaFxqttcZkO56NvIwdD8k2xyIL5YRbCIA5MQ9LOnKN4tpnwb+Q\",\"iv\":\"jQAFJizi1MKZUcCxb6mTCA\"}";
    String encrypted938 = "{\"derivationInfo\":\"Hacking_Password\",\"content\":\"jpNVoXZao+b+f591w+CHWTj7D1M\",\"iv\":\"W+pT37jJP4uDGHmuczXVCA\"}";
    String encrypted939 = "{\"derivationInfo\":\"Database_Password\",\"content\":\"etQQFqMHQQpGr4aDlj5gDjiABkOb\",\"iv\":\"ia+YixjAEqp9W3JEjaYLvQ\"}";
    String encrypted940 = "{\"derivationInfo\":\"General_Password\",\"content\":\"A6kBLXwmx0EVtuIGTzxHiEZ/6yrXgg\",\"iv\":\"e4I0c3fog0TKqTAC2UxYtQ\"}";
    String encrypted941 = "{\"derivationInfo\":\"NonexistentOwner_Pass\",\"content\":\"+Pu1B5YgqGRIHzh17s5tPT3AYb+W\",\"iv\":\"ewRV3RhFfLnbWxY5pr401g\"}";
    String encrypted942 = "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}";
    String encrypted943 = "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}";
    String encrypted944 = "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}";
    String encrypted945 = "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}";
    String encrypted946 = "{\"derivationInfo\":\"Deleted_Secret\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}";
    dslContext
        .insertInto(SECRETS_CONTENT, SECRETS_CONTENT.ID, SECRETS_CONTENT.SECRETID, SECRETS_CONTENT.CREATEDAT, SECRETS_CONTENT.UPDATEDAT, SECRETS_CONTENT.ENCRYPTED_CONTENT, SECRETS_CONTENT.METADATA, SECRETS_CONTENT.ROW_HMAC)
        .values(937L, 737L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:00:47Z").toEpochSecond(), encrypted937, "{\"mode\":\"0400\",\"owner\":\"nobody\"}", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted937, "{\"mode\":\"0400\",\"owner\":\"nobody\"}", 937L)))
        .values(938L, 738L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:01:59Z").toEpochSecond(), encrypted938, "", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted938, "", 938L)))
        .values(939L, 739L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), encrypted939, "", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted939, "", 939L)))
        .values(940L, 740L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), encrypted940, "", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted940, "", 940L)))
        .values(941L, 741L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), encrypted941, "{\"owner\":\"NonExistant\",\"mode\":\"0400\"}", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted941, "{\"owner\":\"NonExistant\",\"mode\":\"0400\"}", 941L)))
        .values(942L, 742L, OffsetDateTime.parse("2011-09-29T15:46:00Z").toEpochSecond(), OffsetDateTime.parse("2015-01-07T12:02:06Z").toEpochSecond(), encrypted942, "", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted942, "", 942L)))
        .values(943L, 742L, OffsetDateTime.parse("2011-09-29T16:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T16:46:00Z").toEpochSecond(), encrypted943, "", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted943, "", 943L)))
        .values(944L, 742L, OffsetDateTime.parse("2011-09-29T17:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T17:46:00Z").toEpochSecond(), encrypted944, "", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted944, "", 944L)))
        .values(945L, 742L, OffsetDateTime.parse("2011-09-29T18:46:00Z").toEpochSecond(), OffsetDateTime.parse("2011-09-29T18:46:00Z").toEpochSecond(), encrypted945, "", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted945, "", 945L)))
        .values(946L, 743L, OffsetDateTime.parse("2016-06-08T02:03:04Z").toEpochSecond(), OffsetDateTime.parse("2016-06-08T02:03:04Z").toEpochSecond(), encrypted946, "", computeRowHmac(SECRETS_CONTENT.getName(), List.of(encrypted946, "", 946L)))
        .execute();

    dslContext
        .insertInto(CLIENTS, CLIENTS.ID, CLIENTS.NAME, CLIENTS.CREATEDAT, CLIENTS.UPDATEDAT, CLIENTS.ENABLED, CLIENTS.AUTOMATIONALLOWED, CLIENTS.ROW_HMAC)
        .values(768L, "client", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, true, computeRowHmac(CLIENTS.getName(), List.of("client", 768L)))
        .values(769L, "CN=User1", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, false, computeRowHmac(CLIENTS.getName(), List.of("CN=User1", 769L)))
        .values(770L, "CN=User2", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(),  OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, false, computeRowHmac(CLIENTS.getName(), List.of("CN=User2", 770L)))
        .values(771L, "CN=User3", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, false, computeRowHmac(CLIENTS.getName(), List.of("CN=User3", 771L)))
        .values(772L, "CN=User4", OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), true, false, computeRowHmac(CLIENTS.getName(), List.of("CN=User4", 772L)))
        .execute();

    dslContext
        .insertInto(ACCESSGRANTS, ACCESSGRANTS.ID, ACCESSGRANTS.GROUPID, ACCESSGRANTS.SECRETID, ACCESSGRANTS.CREATEDAT, ACCESSGRANTS.UPDATEDAT, ACCESSGRANTS.ROW_HMAC)
        .values(617L, 918L, 737L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(918L, 737L)))
        .values(618L, 917L, 737L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(917L, 737L)))
        .values(619L, 916L, 738L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(916L, 738L)))
        .values(620L, 918L, 739L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(918L, 739L)))
        .values(621L, 917L, 739L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(917L, 739L)))
        .values(622L, 918L, 740L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(918L, 740L)))
        .values(623L, 919L, 740L, OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(919L, 740L)))
        .values(624L, 916L, 740L, OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(916L, 740L)))
        .values(625L, 917L, 740L, OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:10Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(917L, 740L)))
        .values(626L, 918L, 741L, OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(918L, 741L)))
        .values(627L, 917L, 741L, OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:11Z").toEpochSecond(), computeRowHmac(ACCESSGRANTS.getName(), List.of(917L, 741L)))
        .execute();

    dslContext
        .insertInto(MEMBERSHIPS, MEMBERSHIPS.ID, MEMBERSHIPS.GROUPID, MEMBERSHIPS.CLIENTID, MEMBERSHIPS.CREATEDAT, MEMBERSHIPS.UPDATEDAT, MEMBERSHIPS.ROW_HMAC)
        .values(659L, 917L, 768L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(768L, 917L)))
        .values(660L, 918L, 769L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(769L, 918L)))
        .values(661L, 916L, 769L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(769L, 916L)))
        .values(662L, 917L, 769L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(769L, 917L)))
        .values(663L, 919L, 770L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(770L, 919L)))
        .values(664L, 917L, 770L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(770L, 917L)))
        .values(665L, 918L, 771L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(771L, 918L)))
        .values(666L, 919L, 771L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(771L, 919L)))
        .values(667L, 918L, 772L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(772L, 918L)))
        .values(668L, 917L, 772L, OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), OffsetDateTime.parse("2012-06-21T14:38:09Z").toEpochSecond(), computeRowHmac(MEMBERSHIPS.getName(), List.of(772L, 917L)))
        .execute();

    new UserDAO(dslContext).createUserAt(
      defaultUser, defaultPassword,
      OffsetDateTime.parse("2012-06-22T14:38:09Z"),
      OffsetDateTime.parse("2012-06-22T14:38:09Z"));
  }
}
