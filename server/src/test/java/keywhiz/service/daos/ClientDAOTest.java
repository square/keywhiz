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

package keywhiz.service.daos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.model.Client;
import keywhiz.service.config.ShadowWrite;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import keywhiz.shadow_write.jooq.tables.Clients;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static keywhiz.jooq.tables.Clients.CLIENTS;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(KeywhizTestRunner.class)
public class ClientDAOTest {
  @Inject DSLContext jooqContext;
  @Inject @ShadowWrite DSLContext jooqShadowWriteContext;
  @Inject ClientDAOFactory clientDAOFactory;

  Client client1, client2;
  ClientDAO clientDAO;

  @Before public void setUp() throws Exception {
    clientDAO = clientDAOFactory.readwrite();
    long now = OffsetDateTime.now().toEpochSecond();

    jooqContext.insertInto(CLIENTS, CLIENTS.NAME, CLIENTS.DESCRIPTION, CLIENTS.CREATEDBY,
        CLIENTS.UPDATEDBY, CLIENTS.ENABLED, CLIENTS.CREATEDAT, CLIENTS.UPDATEDAT)
        .values("client1", "desc1", "creator", "updater", false, now, now)
        .values("client2", "desc2", "creator", "updater", false, now, now)
        .execute();

    client1 = clientDAO.getClient("client1").get();
    client2 = clientDAO.getClient("client2").get();

    try {
      jooqShadowWriteContext.truncate(Clients.CLIENTS).execute();
    } catch (DataAccessException e) {}
    jooqShadowWriteContext.insertInto(Clients.CLIENTS, Clients.CLIENTS.ID, Clients.CLIENTS.NAME,
        Clients.CLIENTS.DESCRIPTION,
        Clients.CLIENTS.CREATEDBY, Clients.CLIENTS.UPDATEDBY, Clients.CLIENTS.ENABLED,
        Clients.CLIENTS.CREATEDAT, Clients.CLIENTS.UPDATEDAT)
        .values(client1.getId(), "client1", "desc1", "creator", "updater", false, now, now)
        .execute();
  }

  @Test public void createClient() {
    int before = tableSize();
    clientDAO.createClient("newClient", "creator", "");
    Client newClient = clientDAO.getClient("newClient").orElseThrow(RuntimeException::new);

    assertThat(tableSize()).isEqualTo(before + 1);
    assertThat(clientDAO.getClients()).containsOnly(client1, client2, newClient);

    // Check that shadow write worked. client2 was directly added to the db, so only
    // client1 and client3 should show up.
    List<Long> r = jooqShadowWriteContext.selectFrom(Clients.CLIENTS).fetch(Clients.CLIENTS.ID);
    assertThat(r).containsOnly(client1.getId(), newClient.getId());
  }

  @Test public void createClientReturnsId() {
    long id = clientDAO.createClient("newClientWithSameId", "creator2", "");
    Client clientById = clientDAO.getClient("newClientWithSameId")
        .orElseThrow(RuntimeException::new);
    assertThat(clientById.getId()).isEqualTo(id);
  }

  @Test public void deleteClient() {
    int before = tableSize();
    clientDAO.deleteClient(client1);
    assertThat(tableSize()).isEqualTo(before - 1);
    assertThat(clientDAO.getClients()).containsOnly(client2);

    // Check that shadow delete worked.
    List<Long> r = jooqShadowWriteContext.selectFrom(Clients.CLIENTS).fetch(Clients.CLIENTS.ID);
    assertThat(r).isEmpty();
  }

  @Test public void getClientByName() {
    assertThat(clientDAO.getClient("client1")).contains(client1);
  }

  @Test public void getNonExistentClientByName() {
    assertThat(clientDAO.getClient("non-existent")).isEmpty();
  }

  @Test public void getClientById() {
    Client client = clientDAO.getClientById(client1.getId()).orElseThrow(RuntimeException::new);
    assertThat(client).isEqualTo(client1);
  }

  @Test public void getNonExistentClientById() {
    assertThat(clientDAO.getClientById(-1)).isEmpty();
  }

  @Test public void getsClients() {
    Set<Client> clients = clientDAO.getClients();
    assertThat(clients).containsOnly(client1, client2);
  }

  private int tableSize() {
    return jooqContext.fetchCount(CLIENTS);
  }
}
