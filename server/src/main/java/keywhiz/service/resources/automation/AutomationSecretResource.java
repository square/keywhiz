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
package keywhiz.service.resources.automation;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.params.LongParam;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import keywhiz.api.AutomationSecretResponse;
import keywhiz.api.CreateSecretRequest;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import keywhiz.service.exceptions.ConflictException;
import keywhiz.service.resources.automation.v2.SecretResource;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.nullToEmpty;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @parentEndpointName secrets-automation
 * @resourceDescription Create, retrieve, and remove secrets
 * @deprecated Will be removed in a future release. Migrate to {@link SecretResource}.
 */
@Deprecated
@Path("/automation/secrets")
@Produces(APPLICATION_JSON)
public class AutomationSecretResource {
  private static final Logger logger = LoggerFactory.getLogger(AutomationSecretResource.class);
  private final SecretController secretController;
  private final SecretDAO secretDAO;
  private final AclDAO aclDAO;

  @Inject public AutomationSecretResource(SecretController secretController,
                                          SecretDAOFactory secretDAOFactory, AclDAOFactory aclDAOFactory) {
    this.secretController = secretController;
    this.secretDAO = secretDAOFactory.readwrite();
    this.aclDAO = aclDAOFactory.readwrite();
  }

  @VisibleForTesting AutomationSecretResource(SecretController secretController,
                                              SecretDAO secretDAO, AclDAO aclDAO) {
    this.secretController = secretController;
    this.secretDAO = secretDAO;
    this.aclDAO = aclDAO;
  }

  /**
   * Create secret
   *
   * @excludeParams automationClient
   * @param request JSON request to formulate the secret
   *
   * @description Creates a secret with the name, content, and metadata from a valid secret request
   * @responseMessage 200 Successfully created secret
   * @responseMessage 409 Secret with given name already exists
   */
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public AutomationSecretResponse createSecret(
      @Auth AutomationClient automationClient,
      @Valid CreateSecretRequest request) {

    SecretController.SecretBuilder builder = secretController.builder(request.name, request.content,
        automationClient.getName(), request.expiry)
        .withDescription(nullToEmpty(request.description));

    if (request.metadata != null) {
      builder.withMetadata(request.metadata);
    }

    Secret secret;
    try {
      secret = builder.create();
    } catch (DataAccessException e) {
      logger.warn(format("Cannot create secret %s", request.name), e);
      throw new ConflictException(format("Cannot create secret %s.", request.name));
    }
    ImmutableList<Group> groups =
        ImmutableList.copyOf(aclDAO.getGroupsFor(secret));

    return AutomationSecretResponse.fromSecret(secret, groups);
  }

  /**
   * Retrieve secret by a specified name, or all secrets if no name given
   * Note that retrieving all secrets could be an expensive query
   *
   * @excludeParams automationClient
   * @optionalParams name
   * @param name the name of the secret to retrieve, if provided
   *
   * @description Returns a single secret or a set of all secrets
   * @responseMessage 200 Found and retrieved secret(s)
   * @responseMessage 404 Secret with given name not found (if name provided)
   */
  @Timed @ExceptionMetered
  @GET
  public ImmutableList<AutomationSecretResponse> readSecrets(
      @Auth AutomationClient automationClient, @QueryParam("name") String name) {

    ImmutableList.Builder<AutomationSecretResponse> responseBuilder = ImmutableList.builder();

    if (name != null) {
      Optional<Secret> optionalSecret = secretController.getSecretByName(name);
      if (!optionalSecret.isPresent()) {
        throw new NotFoundException("Secret not found.");
      }

      Secret secret = optionalSecret.get();
      ImmutableList<Group> groups =
          ImmutableList.copyOf(aclDAO.getGroupsFor(secret));
      responseBuilder.add(AutomationSecretResponse.fromSecret(secret, groups));
    } else {
      List<SanitizedSecret> secrets = secretController.getSanitizedSecrets(null, null);

      for (SanitizedSecret sanitizedSecret : secrets) {
        Secret secret = secretController.getSecretById(sanitizedSecret.id()).orElseThrow(() ->
            new IllegalStateException(format("Cannot find record related to %s", sanitizedSecret)));

        ImmutableList<Group> groups =
            ImmutableList.copyOf(aclDAO.getGroupsFor(secret));
        responseBuilder.add(AutomationSecretResponse.fromSecret(secret, groups));
      }
    }

    return responseBuilder.build();
  }

  /**
   * Retrieve secret by ID
   *
   * @excludeParams automationClient
   * @param secretId the ID of the secret to retrieve
   *
   * @description Returns a single secret if found
   * @responseMessage 200 Found and retrieved secret with given ID
   * @responseMessage 404 Secret with given ID not found
   */
  @Path("{secretId}")
  @Timed @ExceptionMetered
  @GET
  public AutomationSecretResponse readSecretById(
      @Auth AutomationClient automationClient,
      @PathParam("secretId") LongParam secretId) {

    Optional<Secret> secret = secretController.getSecretById(secretId.get());
    if (!secret.isPresent()) {
      throw new NotFoundException("Secret not found.");
    }

    ImmutableList<Group> groups = ImmutableList.copyOf(aclDAO.getGroupsFor(secret.get()));

    return AutomationSecretResponse.fromSecret(secret.get(), groups);
  }

  /**
   * Deletes all versions of a secret series
   *
   * @excludeParams automationClient
   * @param secretName the name of the secret series to delete
   *
   * @description Deletes all versions of a secret series.  This will delete a single secret ID.
   * @responseMessage 200 Deleted secret series
   * @responseMessage 404 Secret series not Found
   */
  @Path("{secretName}")
  @Timed @ExceptionMetered
  @DELETE
  public Response deleteSecretSeries(
      @Auth AutomationClient automationClient,
      @PathParam("secretName") String secretName) {

    secretDAO.getSecretByName(secretName)
        .orElseThrow(() -> new NotFoundException("Secret series not found."));
    secretDAO.deleteSecretsByName(secretName);

    return Response.ok().build();
  }
}
