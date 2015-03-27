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

import com.google.common.base.Throwables;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.java8.auth.Authenticator;
import java.security.Principal;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.NotAuthorizedException;
import keywhiz.api.model.Client;
import keywhiz.service.daos.ClientDAO;
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

  private final Authenticator<String, Client> authenticator;

  @Inject public ClientAuthFactory(ClientDAO clientDAO) {
    this.authenticator = new MyAuthenticator(clientDAO);
  }

  public Client provide(ContainerRequest request) {
    Optional<String> possibleClientName = getClientName(request);
    if (!possibleClientName.isPresent()) {
      throw new NotAuthorizedException("ClientCert not authorized as a Client");
    }
    String clientName = possibleClientName.get();

    try {
      return authenticator.authenticate(clientName)
          .orElseThrow(() -> new NotAuthorizedException(
              format("ClientCert name %s not authorized as a Client", clientName)));
    } catch (AuthenticationException e) {
      throw Throwables.propagate(e);
    }
  }

  static Optional<String> getClientName(ContainerRequest request) {
    Principal principal = request.getSecurityContext().getUserPrincipal();
    if (principal == null) {
      return Optional.empty();
    }

    X500Name name = new X500Name(principal.getName());
    RDN[] rdns = name.getRDNs(BCStyle.CN);
    if (rdns.length == 0) {
      logger.warn("Certificate does not contain CN=xxx,...: {}", principal.getName());
      return Optional.empty();
    }
    return Optional.of(IETFUtils.valueToString(rdns[0].getFirst().getValue()));
  }

  private static class MyAuthenticator implements Authenticator<String, Client> {
    private final ClientDAO clientDAO;

    private MyAuthenticator(ClientDAO clientDAO) {
      this.clientDAO = clientDAO;
    }

    @Override public Optional<Client> authenticate(String name)
        throws AuthenticationException {
      Optional<Client> optionalClient = clientDAO.getClient(name);
      if (optionalClient.isPresent()) {
        Client client = optionalClient.get();
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
      long clientId = clientDAO.createClient(name, "automatic",
          Optional.of("Client created automatically from valid certificate authentication"));
      return Optional.of(clientDAO.getClientById(clientId).get());
    }
  }
}
