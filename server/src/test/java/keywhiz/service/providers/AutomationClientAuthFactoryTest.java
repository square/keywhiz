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

package keywhiz.service.providers;

import java.security.Principal;
import java.util.Optional;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.SecurityContext;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.auth.mutualssl.SimplePrincipal;
import keywhiz.service.daos.ClientDAO;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class AutomationClientAuthFactoryTest {
  @Rule public TestRule mockito = new MockitoJUnitRule(this);

  private static final Principal principal = SimplePrincipal.of("CN=principal,OU=blah");
  private static final Client client =
      new Client(0, "principal", null, null, null, null, null, false, true);
  private static final AutomationClient automationClient = AutomationClient.of(client);

  @Mock ContainerRequest request;
  @Mock SecurityContext securityContext;
  @Mock ClientDAO clientDAO;

  AutomationClientAuthFactory factory;

  @Before public void setUp() {
    factory = new AutomationClientAuthFactory(clientDAO);

    when(request.getSecurityContext()).thenReturn(securityContext);
    when(clientDAO.getClient("principal")).thenReturn(Optional.of(client));
  }

  @Test public void automationClientWhenClientPresent() {
    when(securityContext.getUserPrincipal()).thenReturn(principal);

    assertThat(factory.provide(request)).isEqualTo(automationClient);
  }

  @Test(expected = ForbiddenException.class)
  public void automationClientRejectsClientsWithoutAutomation() {
    Client clientWithoutAutomation =
        new Client(3423, "clientWithoutAutomation", null, null, null, null, null, false, false);

    when(securityContext.getUserPrincipal()).thenReturn(SimplePrincipal.of("CN=clientWithoutAutomation"));
    when(clientDAO.getClient("clientWithoutAutomation"))
        .thenReturn(Optional.of(clientWithoutAutomation));

    factory.provide(request);
  }

  @Test(expected = ForbiddenException.class)
  public void automationClientRejectsClientsWithoutDBEntries() {
    when(securityContext.getUserPrincipal()).thenReturn(SimplePrincipal.of("CN=clientWithoutDBEntry"));
    when(clientDAO.getClient("clientWithoutDBEntry")).thenReturn(Optional.empty());

    factory.provide(request);
  }

  @Test(expected = NotAuthorizedException.class)
  public void automationClientWhenPrincipalAbsent() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    factory.provide(request);
  }
}
