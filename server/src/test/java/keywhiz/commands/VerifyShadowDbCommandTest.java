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

import java.time.OffsetDateTime;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.service.config.ShadowWrite;
import keywhiz.shadow_write.jooq.tables.Accessgrants;
import keywhiz.shadow_write.jooq.tables.Clients;
import keywhiz.shadow_write.jooq.tables.Groups;
import keywhiz.shadow_write.jooq.tables.Memberships;
import keywhiz.shadow_write.jooq.tables.Secrets;
import keywhiz.shadow_write.jooq.tables.SecretsContent;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static keywhiz.jooq.tables.Clients.CLIENTS;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(KeywhizTestRunner.class)
public class VerifyShadowDbCommandTest {
  @Inject DSLContext jooq1;
  @Inject @ShadowWrite DSLContext jooq2;

  @Before public void setUp() {
    try {
      jooq2.truncate(Clients.CLIENTS).execute();
    } catch (DataAccessException e) {}
    try {
      jooq2.truncate(Groups.GROUPS).execute();
    } catch (DataAccessException e) {}
    try {
      jooq2.truncate(Secrets.SECRETS).execute();
    } catch (DataAccessException e) {}
    try {
      jooq2.truncate(SecretsContent.SECRETS_CONTENT).execute();
    } catch (DataAccessException e) {}
    try {
      jooq2.truncate(Memberships.MEMBERSHIPS).execute();
    } catch (DataAccessException e) {}
    try {
      jooq2.truncate(Accessgrants.ACCESSGRANTS).execute();
    } catch (DataAccessException e) {}
  }

  @Test public void testSyncShadowDb() throws Exception {
    long now = OffsetDateTime.now().toEpochSecond();

    // Insert some data
    jooq1.insertInto(CLIENTS)
        .set(CLIENTS.ID, 1L)
        .set(CLIENTS.NAME, "client1")
        .set(CLIENTS.CREATEDAT, now)
        .set(CLIENTS.UPDATEDAT, now)
        .execute();

    // Call verify
    boolean ok = VerifyShadowDbCommand.verify(jooq1, jooq2);
    assertThat(ok).isFalse();

    // Copy the data
    jooq2.insertInto(Clients.CLIENTS)
        .set(Clients.CLIENTS.ID, 1L)
        .set(Clients.CLIENTS.NAME, "client1")
        .set(Clients.CLIENTS.CREATEDAT, now)
        .set(Clients.CLIENTS.UPDATEDAT, now)
        .execute();

    // Call verify
    ok = VerifyShadowDbCommand.verify(jooq1, jooq2);
    assertThat(ok).isTrue();
  }
}
