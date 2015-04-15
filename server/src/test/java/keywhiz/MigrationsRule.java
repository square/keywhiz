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
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import javax.validation.Validation;
import javax.validation.Validator;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mindrot.jbcrypt.BCrypt;

import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static keywhiz.jooq.tables.Users.USERS;

public class MigrationsRule implements TestRule {
  @Override public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override public void evaluate() throws Throwable {
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

        DataSource dataSource = config.getDataSourceFactory()
            .build(new MetricRegistry(), "postgres-migrations");

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.clean();
        flyway.migrate();

        DSLContext dslContext = DSL.using(dataSource.getConnection());

        dslContext
            .insertInto(GROUPS, GROUPS.ID, GROUPS.NAME, GROUPS.CREATEDAT, GROUPS.UPDATEDAT)
            .values(916, "Blackops", OffsetDateTime.parse("2012-06-21T14:38:09.888202Z"), OffsetDateTime.parse("2012-06-21T14:38:09.888202Z"))
            .values(917, "Security", OffsetDateTime.parse("2012-06-21T14:38:09.89200Z"), OffsetDateTime.parse("2012-06-21T14:38:09.892007Z"))
            .values(918, "Web", OffsetDateTime.parse("2012-06-21T14:38:09.893112Z"), OffsetDateTime.parse("2012-06-21T14:38:09.893112Z"))
            .values(919, "iOS", OffsetDateTime.parse("2012-06-21T14:38:09.895656Z"), OffsetDateTime.parse("2012-06-21T14:38:09.895656Z"))
            .values(920, "DeprecatedGroup", OffsetDateTime.parse("2013-03-12T11:23:43.123456Z"), OffsetDateTime.parse("2013-03-12T11:23:43.123456Z"))
            .execute();

        dslContext
            .insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT, SECRETS.UPDATEDAT)
            .values(737, "Nobody_PgPass", OffsetDateTime.parse("2011-09-29T15:46:00.232Z"), OffsetDateTime.parse("2011-09-29T15:46:00.232Z"))
            .values(738, "Hacking_Password", OffsetDateTime.parse("2011-09-29T15:46:00.312Z"), OffsetDateTime.parse("2011-09-29T15:46:00.312Z"))
            .values(739, "Database_Password", OffsetDateTime.parse("2011-09-29T15:46:00.232Z"), OffsetDateTime.parse("2011-09-29T15:46:00.232Z"))
            .values(740, "General_Password", OffsetDateTime.parse("2011-09-29T15:46:00.312Z"), OffsetDateTime.parse("2011-09-29T15:46:00.312Z"))
            .values(741, "NonexistentOwner_Pass", OffsetDateTime.parse("2011-09-29T15:46:00.232Z"), OffsetDateTime.parse("2011-09-29T15:46:00.232Z"))
            .values(742, "Versioned_Password", OffsetDateTime.parse("2011-09-29T15:46:00.232Z"), OffsetDateTime.parse("2011-09-29T15:46:00.232Z"))
            .execute();

        dslContext
            .insertInto(SECRETS_CONTENT, SECRETS_CONTENT.ID, SECRETS_CONTENT.SECRETID, SECRETS_CONTENT.VERSION, SECRETS_CONTENT.CREATEDAT, SECRETS_CONTENT.UPDATEDAT, SECRETS_CONTENT.ENCRYPTED_CONTENT, SECRETS_CONTENT.METADATA)
            .values(937, 737, "", OffsetDateTime.parse("2011-09-29T15:46:00.232Z"), OffsetDateTime.parse("2015-01-07T12:00:47.674786Z"), "{\"derivationInfo\":\"Nobody_PgPass\",\"content\":\"5Eq97Y/6LMLUqH8rlXxEkOeMFmc3cYhQny0eotojNrF3DTFdQPyHVG5HeP5vzaFxqttcZkO56NvIwdD8k2xyIL5YRbCIA5MQ9LOnKN4tpnwb+Q\",\"iv\":\"jQAFJizi1MKZUcCxb6mTCA\"}", "{\"mode\":\"0400\",\"owner\":\"nobody\"}")
            .values(938, 738, "", OffsetDateTime.parse("2011-09-29T15:46:00.312Z"), OffsetDateTime.parse("2015-01-07T12:01:59.335018Z"), "{\"derivationInfo\":\"Hacking_Password\",\"content\":\"jpNVoXZao+b+f591w+CHWTj7D1M\",\"iv\":\"W+pT37jJP4uDGHmuczXVCA\"}", "")
            .values(939, 739, "", OffsetDateTime.parse("2011-09-29T15:46:00.232Z"), OffsetDateTime.parse("2015-01-07T12:02:06.73539Z"), "{\"derivationInfo\":\"Database_Password\",\"content\":\"etQQFqMHQQpGr4aDlj5gDjiABkOb\",\"iv\":\"ia+YixjAEqp9W3JEjaYLvQ\"}", "")
            .values(940, 740, "", OffsetDateTime.parse("2011-09-29T15:46:00.312Z"), OffsetDateTime.parse("2015-01-07T12:02:06.758446Z"), "{\"derivationInfo\":\"General_Password\",\"content\":\"A6kBLXwmx0EVtuIGTzxHiEZ/6yrXgg\",\"iv\":\"e4I0c3fog0TKqTAC2UxYtQ\"}", "")
            .values(941, 741, "", OffsetDateTime.parse("2011-09-29T15:46:00.232Z"), OffsetDateTime.parse("2015-01-07T12:02:06.78574Z"), "{\"derivationInfo\":\"NonexistentOwner_Pass\",\"content\":\"+Pu1B5YgqGRIHzh17s5tPT3AYb+W\",\"iv\":\"ewRV3RhFfLnbWxY5pr401g\"}", "{\"owner\":\"NonExistant\",\"mode\":\"0400\"}")
            .values(942, 742, "0aae825a73e161d8", OffsetDateTime.parse("2011-09-29T15:46:00.232Z"), OffsetDateTime.parse("2015-01-07T12:02:06.806212Z"), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
            .values(943, 742, "0aae825a73e161e8", OffsetDateTime.parse("2011-09-29T16:46:00.232Z"), OffsetDateTime.parse("2011-09-29T16:46:00.232Z"), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
            .values(944, 742, "0aae825a73e161f8", OffsetDateTime.parse("2011-09-29T17:46:00.232Z"), OffsetDateTime.parse("2011-09-29T17:46:00.232Z"), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
            .values(945, 742, "0aae825a73e161g8", OffsetDateTime.parse("2011-09-29T18:46:00.232Z"), OffsetDateTime.parse("2011-09-29T18:46:00.232Z"), "{\"derivationInfo\":\"Versioned_Password\",\"content\":\"GC8/ZvEfqpxhtAkThgZ8/+vPesh9\",\"iv\":\"oRf3CMnB7jv63K33dJFeFg\"}", "")
            .execute();

        dslContext
            .insertInto(CLIENTS, CLIENTS.ID, CLIENTS.NAME, CLIENTS.CREATEDAT, CLIENTS.UPDATEDAT,
                CLIENTS.ENABLED, CLIENTS.AUTOMATIONALLOWED)
            .values(768, "client", OffsetDateTime.parse("2012-06-21T14:38:09.867533Z"), OffsetDateTime.parse("2012-06-21T14:38:09.867533Z"), true, true)
            .values(769, "CN=User1", OffsetDateTime.parse("2012-06-21T14:38:09.872075Z"), OffsetDateTime.parse("2012-06-21T14:38:09.872075Z"), true, false)
            .values(770, "CN=User2", OffsetDateTime.parse("2012-06-21T14:38:09.87328Z"),  OffsetDateTime.parse("2012-06-21T14:38:09.87328Z"), true, false)
            .values(771, "CN=User3", OffsetDateTime.parse("2012-06-21T14:38:09.874214Z"), OffsetDateTime.parse("2012-06-21T14:38:09.874214Z"), true, false)
            .values(772, "CN=User4", OffsetDateTime.parse("2012-06-21T14:38:09.875291Z"), OffsetDateTime.parse("2012-06-21T14:38:09.875291Z"), true, false)
            .execute();

        dslContext
            .insertInto(ACCESSGRANTS, ACCESSGRANTS.ID, ACCESSGRANTS.GROUPID, ACCESSGRANTS.SECRETID, ACCESSGRANTS.CREATEDAT, ACCESSGRANTS.UPDATEDAT)
            .values(617, 918, 737, OffsetDateTime.parse("2012-06-21T14:38:09.984113Z"),
                OffsetDateTime.parse("2012-06-21T14:38:09.984113Z"))
            .values(618, 917, 737, OffsetDateTime.parse("2012-06-21T14:38:09.990935Z"), OffsetDateTime.parse("2012-06-21T14:38:09.990935Z"))
            .values(619, 916, 738, OffsetDateTime.parse("2012-06-21T14:38:09.992612Z"), OffsetDateTime.parse("2012-06-21T14:38:09.992612Z"))
            .values(620, 918, 739, OffsetDateTime.parse("2012-06-21T14:38:09.995025Z"), OffsetDateTime.parse("2012-06-21T14:38:09.995025Z"))
            .values(621, 917, 739, OffsetDateTime.parse("2012-06-21T14:38:09.996522Z"), OffsetDateTime.parse("2012-06-21T14:38:09.996522Z"))
            .values(622, 918, 740, OffsetDateTime.parse("2012-06-21T14:38:09.998356Z"),
                OffsetDateTime.parse("2012-06-21T14:38:09.998356Z"))
            .values(623, 919, 740, OffsetDateTime.parse("2012-06-21T14:38:10.000046Z"), OffsetDateTime.parse("2012-06-21T14:38:10.000046Z"))
            .values(624, 916, 740, OffsetDateTime.parse("2012-06-21T14:38:10.00146Z"),  OffsetDateTime.parse("2012-06-21T14:38:10.00146Z"))
            .values(625, 917, 740, OffsetDateTime.parse("2012-06-21T14:38:10.002938Z"), OffsetDateTime.parse("2012-06-21T14:38:10.002938Z"))
            .values(626, 918, 741, OffsetDateTime.parse("2012-06-21T14:38:11.984113Z"), OffsetDateTime.parse("2012-06-21T14:38:11.984113Z"))
            .values(627, 917, 741, OffsetDateTime.parse("2012-06-21T14:38:11.990935Z"), OffsetDateTime.parse("2012-06-21T14:38:11.990935Z"))
            .execute();

        dslContext
            .insertInto(MEMBERSHIPS, MEMBERSHIPS.ID, MEMBERSHIPS.GROUPID, MEMBERSHIPS.CLIENTID,
                MEMBERSHIPS.CREATEDAT, MEMBERSHIPS.UPDATEDAT)
            .values(659, 917, 768, OffsetDateTime.parse("2012-06-21T14:38:09.957063Z"), OffsetDateTime.parse("2012-06-21T14:38:09.957063Z"))
            .values(660, 918, 769, OffsetDateTime.parse("2012-06-21T14:38:09.970642Z"), OffsetDateTime.parse("2012-06-21T14:38:09.970642Z"))
            .values(661, 916, 769, OffsetDateTime.parse("2012-06-21T14:38:09.972122Z"), OffsetDateTime.parse("2012-06-21T14:38:09.972122Z"))
            .values(662, 917, 769, OffsetDateTime.parse("2012-06-21T14:38:09.974132Z"), OffsetDateTime.parse("2012-06-21T14:38:09.974132Z"))
            .values(663, 919, 770, OffsetDateTime.parse("2012-06-21T14:38:09.975571Z"), OffsetDateTime.parse("2012-06-21T14:38:09.975571Z"))
            .values(664, 917, 770, OffsetDateTime.parse("2012-06-21T14:38:09.976875Z"), OffsetDateTime.parse("2012-06-21T14:38:09.976875Z"))
            .values(665, 918, 771, OffsetDateTime.parse("2012-06-21T14:38:09.978106Z"), OffsetDateTime.parse("2012-06-21T14:38:09.978106Z"))
            .values(666, 919, 771, OffsetDateTime.parse("2012-06-21T14:38:09.979935Z"), OffsetDateTime.parse("2012-06-21T14:38:09.979935Z"))
            .values(667, 918, 772, OffsetDateTime.parse("2012-06-21T14:38:09.981239Z"), OffsetDateTime.parse("2012-06-21T14:38:09.981239Z"))
            .values(668, 917, 772, OffsetDateTime.parse("2012-06-21T14:38:09.982586Z"),
                OffsetDateTime.parse("2012-06-21T14:38:09.982586Z"))
            .execute();

        dslContext.insertInto(USERS)
            .set(USERS.USERNAME, "keywhizAdmin")
            .set(USERS.PASSWORD_HASH, BCrypt.hashpw("adminPass", BCrypt.gensalt()))
            .set(USERS.CREATED_AT, OffsetDateTime.parse("2012-06-22T14:38:09.982586Z"))
            .set(USERS.UPDATED_AT, OffsetDateTime.parse("2012-06-22T14:38:09.982586Z"))
            .execute();

        dataSource.getConnection().close();

        base.evaluate();
      }
    };
  }
}
