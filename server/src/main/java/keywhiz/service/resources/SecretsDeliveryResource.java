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

package keywhiz.service.resources;

import io.dropwizard.auth.Auth;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Client;
import keywhiz.service.config.Readonly;
import keywhiz.service.daos.AclDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;

/**
 * @parentEndpointName secrets
 *
 * @resourceDescription Retrieve a collection of Secrets
 */
@Path("/secrets")
@Produces(MediaType.APPLICATION_JSON)
public class SecretsDeliveryResource {
  private final Logger logger = LoggerFactory.getLogger(SecretsDeliveryResource.class);
  private final AclDAO aclDAO;

  @Inject
  public SecretsDeliveryResource(@Readonly AclDAO aclDAO) {
    this.aclDAO = aclDAO;
  }

  /**
   * Retrieve Secret by name
   *
   * @description Returns all Secrets for the current Client
   */
  @GET
  public List<SecretDeliveryResponse> getSecrets(@Auth Client client) {
    logger.info("Client {} listed available secrets.", client.getName());
    return aclDAO.getSanitizedSecretsFor(client).stream()
        .map(SecretDeliveryResponse::fromSanitizedSecret)
        .collect(toList());
  }
}
