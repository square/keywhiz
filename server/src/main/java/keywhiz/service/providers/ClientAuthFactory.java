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
import javax.ws.rs.NotAuthorizedException;
import keywhiz.api.model.Client;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

/**
 * Authenticates {@link Client}s from requests based on the principal present in a
 * {@link javax.ws.rs.core.SecurityContext} and by querying the database.
 *
 * Modeled similar to io.dropwizard.auth.AuthFactory, however that is not yet usable.
 * See https://github.com/dropwizard/dropwizard/issues/864.
 */
public class ClientAuthFactory {
  private static final Logger logger = LoggerFactory.getLogger(ClientAuthFactory.class);

  private final MyAuthenticator authenticator;

  @Inject public ClientAuthFactory(ClientDAOFactory clientDAOFactory) {
    this.authenticator = new MyAuthenticator(clientDAOFactory.readwrite(), clientDAOFactory.readonly());
  }

  @VisibleForTesting ClientAuthFactory(ClientDAO clientDAO) {
    this.authenticator = new MyAuthenticator(clientDAO, clientDAO);
  }

  public Client provide(ContainerRequest request) {
    Optional<Principal> principal = getPrincipal(request);
    Optional<String> possibleClientName = getClientName(principal);
    if (!principal.isPresent() || !possibleClientName.isPresent()) {
      throw new NotAuthorizedException("ClientCert not authorized as a Client");
    }
    String clientName = possibleClientName.get();

    return authenticator.authenticate(clientName, principal.orElse(null))
        .orElseThrow(() -> new NotAuthorizedException(
            format("ClientCert name %s not authorized as a Client", clientName)));
  }

  static Optional<Principal> getPrincipal(ContainerRequest request) {
    return Optional.ofNullable(request.getSecurityContext().getUserPrincipal());
  }

  static Optional<String> getClientName(Optional<Principal> principal) {
    if (!principal.isPresent()) {
      return Optional.empty();
    }

    X500Name name = new X500Name(principal.get().getName());
    RDN[] rdns = name.getRDNs(BCStyle.CN);
    if (rdns.length == 0) {
      logger.warn("Certificate does not contain CN=xxx,...: {}", principal.get().getName());
      return Optional.empty();
    }
    return Optional.of(IETFUtils.valueToString(rdns[0].getFirst().getValue()));
  }

  private static class MyAuthenticator {
    private final ClientDAO clientDAOReadWrite;
    private final ClientDAO clientDAOReadOnly;

    private MyAuthenticator(
        ClientDAO clientDAOReadWrite,
        ClientDAO clientDAOReadOnly) {
      this.clientDAOReadWrite = clientDAOReadWrite;
      this.clientDAOReadOnly = clientDAOReadOnly;
    }

    public Optional<Client> authenticate(String name, @Nullable Principal principal) {
      Optional<Client> optionalClient = clientDAOReadOnly.getClient(name);
      if (optionalClient.isPresent()) {
        Client client = optionalClient.get();
        clientDAOReadWrite.sawClient(client, principal);
        if (client.isEnabled()) {
          return optionalClient;
        } else {
          logger.warn("Client {} authenticated but disabled via DB", client);
          return Optional.empty();
        }
      }

      /*
       * If a client is seen for the first time, authenticated by certificate, and has no DB entry,
       * then a DB entry is created here. The client can be disabled in the future by flipping the
       * 'enabled' field.
       */
      // TODO(justin): Consider making this behavior configurable.
      long clientId = clientDAOReadWrite.createClient(name, "automatic",
          "Client created automatically from valid certificate authentication");
      return Optional.of(clientDAOReadWrite.getClientById(clientId).get());
    }
  }
}
