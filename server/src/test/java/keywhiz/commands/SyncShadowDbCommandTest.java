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
import keywhiz.jooq.tables.Clients;
import keywhiz.service.config.ShadowWrite;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static keywhiz.shadow_write.jooq.tables.Clients.CLIENTS;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(KeywhizTestRunner.class)
public class SyncShadowDbCommandTest {
  @Inject DSLContext jooqContext;
  @Inject @ShadowWrite DSLContext jooqShadowWriteContext;

  @Before public void setUp() {
    try {
      jooqShadowWriteContext.truncate(CLIENTS).execute();
    } catch (DataAccessException e) {
    }
  }

  @Test public void testSyncShadowDb() throws Exception {
    // Insert some data
    long now = OffsetDateTime.now().toEpochSecond();

    jooqContext.insertInto(Clients.CLIENTS)
        .set(Clients.CLIENTS.ID, 1L)
        .set(Clients.CLIENTS.NAME, "client1")
        .set(Clients.CLIENTS.CREATEDAT, now)
        .set(Clients.CLIENTS.UPDATEDAT, now)
        .execute();

    // Check the data isn't in the shadow db
    assertThat(jooqShadowWriteContext.fetchCount(CLIENTS)).isEqualTo(0);

    int rows = SyncShadowDbCommand.sync(jooqContext, jooqShadowWriteContext);
    assertThat(rows).isEqualTo(1);

    // Check the data is in the shadow db
    assertThat(jooqShadowWriteContext.fetchCount(CLIENTS)).isEqualTo(1);
  }
}
