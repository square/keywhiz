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

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.auth.mutualssl.CertificatePrincipal;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static keywhiz.jooq.tables.Clients.CLIENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(KeywhizTestRunner.class)
public class ClientDAOTest {
  @Inject DSLContext jooqContext;
  @Inject ClientDAOFactory clientDAOFactory;

  Client client1, client2;
  ClientDAO clientDAO;

  @Before public void setUp() {

    clientDAO = clientDAOFactory.readwrite();
    long now = OffsetDateTime.now().toEpochSecond();

    jooqContext.insertInto(CLIENTS, CLIENTS.NAME, CLIENTS.DESCRIPTION, CLIENTS.CREATEDBY,
        CLIENTS.UPDATEDBY, CLIENTS.ENABLED, CLIENTS.CREATEDAT, CLIENTS.UPDATEDAT)
        .values("client1", "desc1", "creator", "updater", false, now, now)
        .values("client2", "desc2", "creator", "updater", false, now, now)
        .execute();

    client1 = clientDAO.getClientByName("client1").get();
    client2 = clientDAO.getClientByName("client2").get();
  }

  @Test public void createClient() throws Exception {
    int before = tableSize();
    clientDAO.createClient("newClient", "creator", "",
        new URI("spiffe://test.env.com/newClient"));
    Client newClient = clientDAO.getClientByName("newClient").orElseThrow(RuntimeException::new);

    assertThat(tableSize()).isEqualTo(before + 1);
    assertThat(clientDAO.getClients()).containsOnly(client1, client2, newClient);

    assertThat(newClient.getCreatedBy()).isEqualTo("creator");
    assertThat(newClient.getSpiffeId()).isEqualTo("spiffe://test.env.com/newClient");
  }

  @Test public void createClientReturnsId() {
    long id = clientDAO.createClient("newClientWithSameId", "creator2", "", null);
    Client clientByName = clientDAO.getClientByName("newClientWithSameId")
        .orElseThrow(RuntimeException::new);
    assertThat(clientByName.getId()).isEqualTo(id);
  }

  @Test public void createClientDropsEmptyStringSpiffeId() throws Exception {
    clientDAO.createClient("firstWithoutSpiffe", "creator2", "", new URI(""));
    // This creation should not fail, because an empty SPIFFE ID should be converted to null
    clientDAO.createClient("secondWithoutSpiffe", "creator2", "", new URI(""));
  }

  @Test public void deleteClient() {
    int before = tableSize();
    clientDAO.deleteClient(client1);
    assertThat(tableSize()).isEqualTo(before - 1);
    assertThat(clientDAO.getClients()).containsOnly(client2);
  }

  @Test public void getClientByName() {
    assertThat(clientDAO.getClientByName("client1")).contains(client1);
  }

  @Test public void getNonExistentClientByName() {
    assertThat(clientDAO.getClientByName("non-existent")).isEmpty();
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

  @Test public void sawClientTest() {
    assertThat(client1.getLastSeen()).isNull();
    assertThat(client2.getLastSeen()).isNull();

    Instant expiration = Instant.now();
    // Remove nanos because database drops it on storage, and we want
    // to compare later to make sure the proper expiration was set in DB.
    expiration = expiration.minusNanos(expiration.get(NANO_OF_SECOND));

    CertificatePrincipal principal = mock(CertificatePrincipal.class);
    when(principal.getCertificateExpiration()).thenReturn(expiration);

    ApiDate now = ApiDate.now();
    clientDAO.sawClient(client1, principal);

    // reload clients from db, as sawClient doesn't update in-memory object
    Client client1v2 = clientDAO.getClientByName(client1.getName()).get();
    Client client2v2 = clientDAO.getClientByName(client2.getName()).get();

    // verify client1 from db has updated lastSeen, and client2 hasn't changed
    assertThat(client1v2.getLastSeen()).isNotNull();
    assertTrue(client1v2.getLastSeen().toEpochSecond() >= now.toEpochSecond());
    assertThat(client2v2.getLastSeen()).isNull();
    assertThat(client1v2.getExpiration()).isNotNull();
    assertThat(client1v2.getExpiration().toInstant()).isEqualTo(expiration);
    assertThat(client2v2.getExpiration()).isNull();
  }


  private int tableSize() {
    return jooqContext.fetchCount(CLIENTS);
  }
}
