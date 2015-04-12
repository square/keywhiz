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
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import keywhiz.api.model.AutomationClient;
import keywhiz.service.daos.ClientJooqDao;
import org.glassfish.jersey.server.ContainerRequest;

import static java.lang.String.format;

/**
 * Authenticates {@link AutomationClient}s from requests based on the principal present in a
 * {@link javax.ws.rs.core.SecurityContext} and by querying the database.
 *
 * Modeled similar to io.dropwizard.auth.AuthFactory, however that is not yet usable.
 * See https://github.com/dropwizard/dropwizard/issues/864.
 */
public class AutomationClientAuthFactory {
  private final Authenticator<String, AutomationClient> authenticator;

  @Inject public AutomationClientAuthFactory(ClientJooqDao clientJooqDao) {
    this.authenticator = new MyAuthenticator(clientJooqDao);
  }

  public AutomationClient provide(ContainerRequest request) {
    Optional<String> possibleClientName = ClientAuthFactory.getClientName(request);
    if (!possibleClientName.isPresent()) {
      throw new NotAuthorizedException(format("Not authorized as a AutomationClient"));
    }
    String clientName = possibleClientName.get();

    try {
      return authenticator.authenticate(clientName)
          .orElseThrow(() -> new ForbiddenException(
              format("ClientCert name %s not authorized as a AutomationClient", clientName)));
    } catch (AuthenticationException e) {
      throw Throwables.propagate(e);
    }
  }

  private static class MyAuthenticator implements Authenticator<String, AutomationClient> {
    private final ClientJooqDao clientJooqDao;

    private MyAuthenticator(ClientJooqDao clientJooqDao) {
      this.clientJooqDao = clientJooqDao;
    }

    @Override public Optional<AutomationClient> authenticate(String name)
        throws AuthenticationException {
      return clientJooqDao.getClient(name).map(AutomationClient::of);
    }
  }
}
