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
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.SecurityContext;
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class ClientAuthFactoryTest {
  @Rule public TestRule mockito = new MockitoJUnitRule(this);

  private static final Principal principal = SimplePrincipal.of("CN=principal,OU=organizational-unit");
  private static final Client client =
      new Client(0, "principal", null, null, null, null, null, true, false);

  @Mock ContainerRequest request;
  @Mock SecurityContext securityContext;
  @Mock ClientDAO clientDAO;

  ClientAuthFactory factory;

  @Before public void setUp() {
    factory = new ClientAuthFactory(clientDAO);

    when(request.getSecurityContext()).thenReturn(securityContext);
    when(clientDAO.getClient("principal")).thenReturn(Optional.of(client));
  }

  @Test public void clientWhenClientPresent() {
    when(securityContext.getUserPrincipal()).thenReturn(principal);

    assertThat(factory.provide(request)).isEqualTo(client);
  }

  @Test(expected = NotAuthorizedException.class)
  public void clientWhenPrincipalAbsentThrows() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    factory.provide(request);
  }

  @Test(expected = NotAuthorizedException.class)
  public void rejectsDisabledClients() {
    Client disabledClient =new Client(1, "disabled", null, null, null, null, null,
        false /* disabled */, false);

    when(securityContext.getUserPrincipal()).thenReturn(SimplePrincipal.of("CN=disabled"));
    when(clientDAO.getClient("disabled")).thenReturn(Optional.of(disabledClient));

    factory.provide(request);
  }

  @Test public void createsDbRecordForNewClient() throws Exception {
    OffsetDateTime now = OffsetDateTime.now();
    Client newClient = new Client(2345L, "new-client", "desc", now, "automatic", now, "automatic",
        true, false);

    // lookup doesn't find client
    when(securityContext.getUserPrincipal()).thenReturn(SimplePrincipal.of("CN=new-client"));
    when(clientDAO.getClient("new-client")).thenReturn(Optional.empty());

    // a new DB record is created
    when(clientDAO.createClient(eq("new-client"), eq("automatic"), any())).thenReturn(2345L);
    when(clientDAO.getClientById(2345L)).thenReturn(Optional.of(newClient));

    assertThat(factory.provide(request)).isEqualTo(newClient);
  }
}
