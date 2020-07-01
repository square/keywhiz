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

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.auth.Auth;
import keywhiz.api.BatchSecretRequest;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.service.config.Readonly;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import keywhiz.service.daos.SecretController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import java.util.Iterator;
import java.util.List;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * parentEndpointName secret
 * <p>
 * resourceDescription Retrieve Batched Named Secrets
 */
@Path("/batchsecret")
@Produces(APPLICATION_JSON)
public class BatchSecretDeliveryResource {
  private static final Logger logger = LoggerFactory.getLogger(BatchSecretDeliveryResource.class);

  private final SecretController secretController;
  private final AclDAO aclDAO;
  private final ClientDAO clientDAO;

  @Inject
  public BatchSecretDeliveryResource(@Readonly SecretController secretController,
                                     AclDAOFactory aclDAOFactory, ClientDAOFactory clientDAOFactory) {
    this.secretController = secretController;
    this.aclDAO = aclDAOFactory.readonly();
    this.clientDAO = clientDAOFactory.readonly();
  }

  @VisibleForTesting
  BatchSecretDeliveryResource(SecretController secretController, AclDAO aclDAO,
                              ClientDAO clientDAO) {
    this.secretController = secretController;
    this.aclDAO = aclDAO;
    this.clientDAO = clientDAO;
  }

  /**
   * Retrieve Secret by name
   *
   * @param secrets the name of the Secrets to retrieve in batch
   * @param client  the client performing the retrieval
   * @return the secret with the specified name, if present and accessible to the client
   * <p>
   * responseMessage 200 Found and retrieved Secret with given name
   * responseMessage 403 Secret is not assigned to Client
   * responseMessage 404 Secret with given name not found
   * responseMessage 500 Secret response could not be generated for given Secret
   */
  @Timed
  @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public List<SecretDeliveryResponse> getBatchSecret(@Auth Client client, @Valid BatchSecretRequest request) {


    List<SanitizedSecret> clientAccessibleSecrets = aclDAO.getBatchSanitizedSecretsFor(client, request.secrets());
    List<Secret> existingSecrets = secretController.getSecretsByName(request.secrets());

    boolean clientExists = clientDAO.getClient(client.getName()).isPresent();

    // The request fails whenever a single secret is requested that is not accessible
    // The client is responsible for only requesting secrets they have permission for
    for (String secretname : request.secrets()) {
      boolean secretExists = existingSecrets.stream().anyMatch(s -> s.getName().equals(secretname));
      boolean secretAccessible = clientAccessibleSecrets.stream().anyMatch(s -> s.name().equals(secretname));

      if (!secretAccessible) {
        if (clientExists && secretExists) {
          throw new ForbiddenException(format("Access denied: %s at '%s' by '%s'", client.getName(),
                  "/batchsecret " + secretname, client));
        } else {
          if (clientExists) {
            logger.info("Client {} requested unknown secret {}", client.getName(), secretname);
          }
          throw new NotFoundException();
        }
      } else {
        if (!secretExists) {
          // If the secret is accessible but does not exist,
          // this indicates a Keywhiz issue. This is not a client issue.
          logger.warn("Client {} requested secret which is not retrievable {}", client.getName(), secretname);
          throw new NotFoundException("Secret not found.");
        }
      }
    }

    logger.info("Client {} granted access to {}.", client.getName(), clientAccessibleSecrets.stream().map(s -> s.name()).collect(toList()));
    try {
      return existingSecrets.stream()
              .map(SecretDeliveryResponse::fromSecret)
              .collect(toList());
    } catch (IllegalArgumentException e) {
      logger.error(format("Failed creating batch response for secrets %s", existingSecrets.stream().map(s -> s.getName()).collect(toList())), e);
      throw new InternalServerErrorException();
    }
  }
}

