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
package keywhiz.service.resources.automation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import keywhiz.api.ApiDate;
import keywhiz.api.ClientDetailResponse;
import keywhiz.api.CreateClientRequest;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
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
import static org.mockito.Mockito.when;

public class AutomationClientResourceTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock ClientDAO clientDAO;
  @Mock AclDAO aclDAO;
  ApiDate now = ApiDate.now();
  AutomationClient automation = AutomationClient.of(
      new Client(1, "automation", "Automation client", now, "test", now, "test", null, true, true));
  AuditLog auditLog = new SimpleLogger();

  AutomationClientResource resource;

  @Before public void setUp() {
    resource = new AutomationClientResource(clientDAO, aclDAO, auditLog);
  }

  @Test public void findClientByName() {
    Client client = new Client(2, "client", "2nd client", now, "test", now, "test", null, true, false);
    Group firstGroup = new Group(1, "first Group", "testing group", now, "client", now, "client",
        ImmutableMap.of("app", "keywhiz"));
    Group secondGroup = new Group(2, "second Group", "testing group", now, "client", now, "client",
        ImmutableMap.of("app", "keywhiz"));
    ClientDetailResponse expectedClient = ClientDetailResponse.fromClient(client,
        ImmutableList.of(firstGroup, secondGroup), ImmutableList.of());

    when(clientDAO.getClient("client")).thenReturn(Optional.of(client));
    when(aclDAO.getGroupsFor(client)).thenReturn(ImmutableSet.of(firstGroup, secondGroup));

    Response response = resource.findClient(automation, Optional.of("client"));
    assertThat(response.getEntity()).hasSameClassAs(expectedClient);
    ClientDetailResponse actualResponse = (ClientDetailResponse) response.getEntity();
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedClient);
  }

  @Test(expected = NotFoundException.class)
  public void findClientByNameNotFound() {
    when(clientDAO.getClient("client")).thenReturn(Optional.empty());
    resource.findClient(automation, Optional.of("client"));
  }

  @Test public void createNewClient() {
    Client client = new Client(543L, "client", "2nd client", now, "test", now, "test", null, true, false);

    CreateClientRequest request = new CreateClientRequest("client");

    when(clientDAO.getClient("client")).thenReturn(Optional.empty());
    when(clientDAO.createClient("client", automation.getName(), "")).thenReturn(543L);
    when(clientDAO.getClientById(543L)).thenReturn(Optional.of(client));
    when(aclDAO.getGroupsFor(client)).thenReturn(ImmutableSet.of());

    ClientDetailResponse response = ClientDetailResponse.fromClient(client, ImmutableList.of(),
        ImmutableList.of());
    ClientDetailResponse response1 = resource.createClient(automation, request);

    assertThat(response.name).isEqualTo(response1.name);
  }

  @Test public void createNewClientAlreadyExists() {
    Client client = new Client(543L, "client", "2nd client", now, "test", now, "test", null, true, false);

    CreateClientRequest request = new CreateClientRequest("client");

    when(clientDAO.getClient("client")).thenReturn(Optional.empty());
    when(clientDAO.createClient("client", automation.getName(), "")).thenReturn(543L);
    when(clientDAO.getClientById(543L)).thenReturn(Optional.of(client));

    ClientDetailResponse response = ClientDetailResponse.fromClient(client, ImmutableList.of(),
        ImmutableList.of());
    ClientDetailResponse response1 = resource.createClient(automation, request);

    assertThat(response.name).isEqualTo(response1.name);
  }
}
