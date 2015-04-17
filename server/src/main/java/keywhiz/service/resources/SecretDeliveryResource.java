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
import java.text.ParseException;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.service.config.Readonly;
import keywhiz.service.daos.AclJooqDao;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.SecretController;
import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static keywhiz.api.model.Secret.splitNameAndVersion;

/**
 * @parentEndpointName secret
 *
 * @resourceDescription Retrieve Secret by name
 */
@Path("/secret/{secretName}")
@Produces(MediaType.APPLICATION_JSON)
public class SecretDeliveryResource {
  private final Logger logger = LoggerFactory.getLogger(SecretDeliveryResource.class);
  private final SecretController secretController;
  private final AclJooqDao aclJooqDao;
  private final ClientDAO clientDAO;

  @Inject
  public SecretDeliveryResource(@Readonly SecretController secretController,
      @Readonly AclJooqDao aclJooqDao, @Readonly ClientDAO clientDAO) {
    this.secretController = secretController;
    this.aclJooqDao = aclJooqDao;
    this.clientDAO = clientDAO;
  }

  /**
   * Retrieve Secret by name
   *
   * @param secretName the name of the Secret to retrieve
   *
   * @description Returns a single Secret if found
   * @responseMessage 200 Found and retrieved Secret with given name
   * @responseMessage 403 Secret is not assigned to Client
   * @responseMessage 404 Secret with given name not found
   * @responseMessage 500 Secret response could not be generated for given Secret
   */
  @GET
  public SecretDeliveryResponse getSecret(@NotEmpty @PathParam("secretName") String secretName,
                                          @Auth Client client) {
    String[] parts;
    try {
      parts = splitNameAndVersion(secretName);
    } catch (ParseException e) {
      throw new BadRequestException(format("Invalid secret name '%s'", secretName));
    }
    String name = parts[0];
    String version = parts[1];

    Optional<SanitizedSecret> sanitizedSecret = aclJooqDao.getSanitizedSecretFor(client, name, version);
    Optional<Secret> secret = secretController.getSecretByNameAndVersion(name, version);

    if (!sanitizedSecret.isPresent()) {
      boolean clientExists = clientDAO.getClient(client.getName()).isPresent();
      boolean secretExists = secret.isPresent();

      if (clientExists && secretExists) {
        throw new ForbiddenException(format("Access denied: %s at '%s' by '%s'", client.getName(),
                "/secret/" + secretName, client));
      } else {
        throw new NotFoundException();
      }
    }

    logger.info("Client {} granted access to {}.", client.getName(), secretName);
    try {
      return SecretDeliveryResponse.fromSecret(secret.get());
    } catch (IllegalArgumentException e) {
      logger.error("Failed creating response for secret {}: {}", secretName, e);
      throw new InternalServerErrorException();
    }
  }
}
