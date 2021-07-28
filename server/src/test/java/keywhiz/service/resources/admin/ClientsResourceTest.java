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

package keywhiz.service.resources.admin;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.dropwizard.jersey.params.LongParam;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import keywhiz.api.ApiDate;
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.automation.v2.CreateClientRequestV2;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.auth.User;
import keywhiz.log.AuditLog;
import keywhiz.log.SimpleLogger;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.ClientDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClientsResourceTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock AclDAO aclDAO;
  @Mock ClientDAO clientDAO;

  User user = User.named("user");
  ApiDate now = ApiDate.now();
  Client client =
      new Client(1, "client", "1st client", null, now, "test", now, "test", null, null, true, false
      );

  AuditLog auditLog = new SimpleLogger();

  ClientsResource resource;

  @Before public void setUp() {
    resource = new ClientsResource(aclDAO, clientDAO, auditLog);
  }

  @Test public void listClients() {
    Client client1 =
        new Client(1, "client", "1st client", null, now, "test", now, "test", null, null, true,
            false
        );
    Client client2 =
        new Client(2, "client2", "2nd client", null, now, "test", now, "test", null, null, true,
            false
        );

    when(clientDAO.getClients()).thenReturn(ImmutableSet.of(client1, client2));

    List<Client> response = resource.listClients(user);
    assertThat(response).containsOnly(client1, client2);
  }

  @Test public void createsClient() throws Exception {
    CreateClientRequestV2 request = CreateClientRequestV2.builder()
        .name("new-client-name")
        .description("description")
        .spiffeId("spiffe//example.org/new-client-name")
        .build();
    when(clientDAO.createClient("new-client-name", "user", "description",
        new URI("spiffe//example.org/new-client-name"))).thenReturn(42L);
    when(clientDAO.getClientById(42L)).thenReturn(Optional.of(client));
    when(aclDAO.getSanitizedSecretsFor(client)).thenReturn(ImmutableSet.of());

    Response response = resource.createClient(user, request);
    assertThat(response.getStatus()).isEqualTo(201);
  }

  @Test public void includesTheClient() {
    when(clientDAO.getClientById(1)).thenReturn(Optional.of(client));
    when(aclDAO.getGroupsFor(client)).thenReturn(Collections.emptySet());
    when(aclDAO.getSanitizedSecretsFor(client)).thenReturn(ImmutableSet.of());

    ClientDetailResponse response = resource.getClient(user, new LongParam("1"));

    assertThat(response.id).isEqualTo(client.getId());
    assertThat(response.name).isEqualTo(client.getName());
    assertThat(response.description).isEqualTo(client.getDescription());
    assertThat(response.creationDate).isEqualTo(client.getCreatedAt());
    assertThat(response.createdBy).isEqualTo(client.getCreatedBy());
    assertThat(response.updateDate).isEqualTo(client.getUpdatedAt());
    assertThat(response.updatedBy).isEqualTo(client.getUpdatedBy());
  }

  @Test public void handlesNoAssociations() {
    when(clientDAO.getClientById(1)).thenReturn(Optional.of(client));
    when(aclDAO.getGroupsFor(client)).thenReturn(Collections.emptySet());
    when(aclDAO.getSanitizedSecretsFor(client)).thenReturn(ImmutableSet.of());

    ClientDetailResponse response = resource.getClient(user, new LongParam("1"));
    assertThat(response.groups).isEmpty();
    assertThat(response.secrets).isEmpty();
  }

  @Test public void includesAssociations() {
    Group group1 = new Group(0, "group1", null, null, null, null, null, null);
    Group group2 = new Group(0, "group2", null, null, null, null, null, null);
    Secret secret =
        new Secret(15, "secret", null, null, () -> "supersecretdata", "checksum", now, "creator", now,
            "updater", null, null, null, 0, 1L, now, "updater");

    when(clientDAO.getClientById(1)).thenReturn(Optional.of(client));
    when(aclDAO.getGroupsFor(client)).thenReturn(Sets.newHashSet(group1, group2));
    when(aclDAO.getSanitizedSecretsFor(client))
        .thenReturn(ImmutableSet.of(SanitizedSecret.fromSecret(secret)));

    ClientDetailResponse response = resource.getClient(user, new LongParam("1"));
    assertThat(response.groups).containsOnly(group1, group2);
    assertThat(response.secrets).containsOnly(SanitizedSecret.fromSecret(secret));
  }

  @Test public void findClientByName() {
    when(clientDAO.getClientByName(client.getName())).thenReturn(Optional.of(client));
    assertThat(resource.getClientByName(user, "client")).isEqualTo(client);
  }

  @Test(expected = NotFoundException.class)
  public void badIdNotFound() {
    when(clientDAO.getClientById(41)).thenReturn(Optional.empty());
    resource.getClient(user, new LongParam("41"));
  }

  @Test(expected = NotFoundException.class)
  public void notFoundWhenRetrievingBadName() {
    when(clientDAO.getClientByName("non-existent-client")).thenReturn(Optional.empty());
    resource.getClientByName(user, "non-existent-client");
  }

  @Test public void deleteCallsDelete() {
    when(clientDAO.getClientById(12)).thenReturn(Optional.of(client));

    Response blah = resource.deleteClient(user, new LongParam("12"));
    verify(clientDAO).deleteClient(client);
    assertThat(blah.getStatus()).isEqualTo(204);
  }
}
