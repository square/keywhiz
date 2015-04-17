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

import java.util.Optional;
import java.util.Set;
import keywhiz.TestDBRule;
import keywhiz.api.model.Client;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static keywhiz.jooq.tables.Clients.CLIENTS;
import static org.assertj.core.api.Assertions.assertThat;

public class ClientDAOTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();

  Client client1, client2;
  ClientDAO clientDAO;

  @Before
  public void setUp() throws Exception {
    clientDAO = new ClientDAO(testDBRule.jooqContext());

    testDBRule.jooqContext().delete(CLIENTS).execute();

    testDBRule.jooqContext().insertInto(CLIENTS,
        CLIENTS.NAME, CLIENTS.DESCRIPTION, CLIENTS.CREATEDBY, CLIENTS.UPDATEDBY, CLIENTS.ENABLED)
        .values("client1", "desc1", "creator", "updater", false)
        .values("client2", "desc2", "creator", "updater", false)
        .execute();

    client1 = clientDAO.getClient("client1").get();
    client2 = clientDAO.getClient("client2").get();
  }

  @Test
  public void createClient() {
    int before = tableSize();
    clientDAO.createClient("newClient", "creator", Optional.empty());
    Client newClient = clientDAO.getClient("newClient").orElseThrow(RuntimeException::new);

    assertThat(tableSize()).isEqualTo(before + 1);
    assertThat(clientDAO.getClients()).containsOnly(client1, client2, newClient);
  }

  @Test
  public void createClientReturnsId() {
    long id = clientDAO.createClient("newClientWithSameId", "creator2", Optional.empty());
    Client clientById = clientDAO.getClient("newClientWithSameId")
        .orElseThrow(RuntimeException::new);
    assertThat(clientById.getId()).isEqualTo(id);
  }

  @Test
  public void deleteClient() {
    int before = tableSize();
    clientDAO.deleteClient(client1);
    assertThat(tableSize()).isEqualTo(before - 1);
    assertThat(clientDAO.getClients()).containsOnly(client2);
  }

  @Test
  public void getClientByName() {
    Client client = clientDAO.getClient("client1").orElseThrow(RuntimeException::new);
    assertThat(client).isEqualTo(client1);
  }

  @Test
  public void getNonExistentClientByName() {
    assertThat(clientDAO.getClient("non-existent").isPresent()).isFalse();
  }

  @Test
  public void getClientById() {
    Client client = clientDAO.getClientById(client1.getId()).orElseThrow(RuntimeException::new);
    assertThat(client).isEqualTo(client1);
  }

  @Test
  public void getNonExistentClientById() {
    assertThat(clientDAO.getClientById(-1).isPresent()).isFalse();
  }

  @Test
  public void getsClients() {
    Set<Client> clients = clientDAO.getClients();
    assertThat(clients).containsOnly(client1, client2);
  }

  private int tableSize() {
    return testDBRule.jooqContext().fetchCount(CLIENTS);
  }
}
