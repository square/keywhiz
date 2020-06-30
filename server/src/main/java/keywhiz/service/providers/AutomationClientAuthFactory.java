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

import com.google.common.annotations.VisibleForTesting;
import java.security.Principal;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import org.glassfish.jersey.server.ContainerRequest;

import static java.lang.String.format;

/**
 * Authenticates {@link AutomationClient}s from requests based on the principal present in a {@link
 * javax.ws.rs.core.SecurityContext} and by querying the database.
 * <p>
 * Modeled similar to io.dropwizard.auth.AuthFactory, however that is not yet usable. See
 * https://github.com/dropwizard/dropwizard/issues/864.
 */
public class AutomationClientAuthFactory {
  private final MyAuthenticator authenticator;

  @Inject public AutomationClientAuthFactory(ClientDAOFactory clientDAOFactory) {
    this.authenticator =
        new MyAuthenticator(clientDAOFactory.readwrite(), clientDAOFactory.readonly());
  }

  @VisibleForTesting AutomationClientAuthFactory(ClientDAO clientDAO) {
    this.authenticator = new MyAuthenticator(clientDAO, clientDAO);
  }

  public AutomationClient provide(ContainerRequest request) {
    Principal principal = ClientAuthFactory.getPrincipal(request)
        .orElseThrow(() -> new NotAuthorizedException("Not authorized as a AutomationClient"));
    String clientName = ClientAuthenticator.getClientName(principal)
        .orElseThrow(() -> new NotAuthorizedException("Not authorized as a AutomationClient"));

    return authenticator.authenticate(clientName, principal)
        .orElseThrow(() -> new ForbiddenException(
            format("ClientCert name %s not authorized as a AutomationClient", clientName)));
  }

  private static class MyAuthenticator {
    private final ClientDAO clientDAOReadWrite;
    private final ClientDAO clientDAOReadOnly;

    private MyAuthenticator(ClientDAO clientDAOReadWrite, ClientDAO clientDAOReadOnly) {
      this.clientDAOReadWrite = clientDAOReadWrite;
      this.clientDAOReadOnly = clientDAOReadOnly;
    }

    public Optional<AutomationClient> authenticate(String name, @Nullable Principal principal) {
      Optional<Client> client = clientDAOReadOnly.getClientByName(name);
      client.ifPresent(value -> clientDAOReadWrite.sawClient(value, principal));
      return client.map(AutomationClient::of);
    }
  }
}
