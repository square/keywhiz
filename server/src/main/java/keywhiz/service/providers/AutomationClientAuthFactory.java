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
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ForbiddenException;
import keywhiz.KeywhizConfig;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.service.config.ClientAuthConfig;
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
public class AutomationClientAuthFactory extends ClientAuthFactory {
  @Inject public AutomationClientAuthFactory(ClientDAOFactory clientDAOFactory,
      KeywhizConfig keywhizConfig) {
    super(clientDAOFactory, keywhizConfig);
  }

  @VisibleForTesting AutomationClientAuthFactory(ClientDAO clientDAO,
      ClientAuthConfig clientAuthConfig) {
    super(clientDAO, clientAuthConfig);
  }

  public AutomationClient provide(ContainerRequest containerRequest,
      HttpServletRequest httpServletRequest) {
    // This will throw a NotAuthorizedException if the client does not exist or cannot
    // be extracted from the request.
    Client client = super.provide(containerRequest, httpServletRequest);

    // This method returns null if the provided client is not actually an automation client
    return Optional.ofNullable(AutomationClient.of(client))
        .orElseThrow(() -> new ForbiddenException(
            format("Client %s not authorized as a AutomationClient", client.getName())));
  }

  @Override
  protected boolean createMissingClient() {
    // Automation clients should not be automatically created if they are missing
    return false;
  }
}
