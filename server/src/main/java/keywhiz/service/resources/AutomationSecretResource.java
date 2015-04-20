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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import keywhiz.api.AutomationSecretResponse;
import keywhiz.api.CreateSecretRequest;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.VersionGenerator;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretSeriesDAO;
import keywhiz.service.exceptions.ConflictException;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

/**
 * @parentEndpointName secrets-automation
 *
 * @resourcePath /automation/secrets
 * @resourceDescription Create, retrieve, and remove secrets
 */
@Path("/automation/secrets")
@Produces(MediaType.APPLICATION_JSON)
public class AutomationSecretResource {
  private static final Logger logger = LoggerFactory.getLogger(AutomationSecretResource.class);
  private final SecretController secretController;
  private final AclDAO aclDAO;
  private final SecretSeriesDAO secretSeriesDAO;

  @Inject
  public AutomationSecretResource(SecretController secretController, AclDAO aclDAO,
      SecretSeriesDAO secretSeriesDAO) {
    this.secretController = secretController;
    this.aclDAO = aclDAO;
    this.secretSeriesDAO = secretSeriesDAO;
  }

  /**
   * Create secret
   *
   * @param request the JSON secret request used to formulate the secret
   *
   * @description Creates a secret with the name, content, and metadata from a valid secret request
   * @responseMessage 200 Successfully created secret
   * @responseMessage 409 Secret with given name already exists
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public AutomationSecretResponse createSecret(
      @Auth AutomationClient automationClient,
      @Valid CreateSecretRequest request) {

    SecretController.SecretBuilder builder = secretController.builder(request.name, request.content,
        automationClient.getName());

    if (request.description != null) {
      builder.withDescription(request.description);
    }

    if (request.withVersion) {
      builder.withVersion(VersionGenerator.now().toHex());
    }

    if (request.metadata != null) {
      builder.withMetadata(request.metadata);
    }

    Secret secret;
    try {
      secret = builder.build();
    } catch (DataAccessException e) {
      logger.warn("Cannot create secret {}: {}", request.name, e);
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
   * @optionalParams name
   * @param name the name of the secret to retrieve, if provided
   *
   * @description Returns a single secret or a set of all secrets
   * @responseMessage 200 Found and retrieved secret(s)
   * @responseMessage 404 Secret with given name not found (if name provided)
   */
  @GET
  public ImmutableList<AutomationSecretResponse> readSecrets(
      @Auth AutomationClient automationClient, @QueryParam("name") String name) {

    ImmutableList.Builder<AutomationSecretResponse> responseBuilder = ImmutableList.builder();

    if (name != null) {
      Optional<Secret> optionalSecret = secretController.getSecretByNameAndVersion(name, "");
      if (!optionalSecret.isPresent()) {
        throw new NotFoundException("Secret not found.");
      }

      Secret secret = optionalSecret.get();
      ImmutableList<Group> groups =
          ImmutableList.copyOf(aclDAO.getGroupsFor(secret));
      responseBuilder.add(AutomationSecretResponse.fromSecret(secret, groups));
    } else {
      List<SanitizedSecret> secrets = secretController.getSanitizedSecrets();

      for (SanitizedSecret sanitizedSecret : secrets) {
        Secret secret = secretController.getSecretByIdAndVersion(
            sanitizedSecret.id(), sanitizedSecret.version()).orElseThrow(
            () -> new IllegalStateException("Cannot find record related to " + sanitizedSecret));

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
   * @param secretId the ID of the secret to retrieve
   *
   * @description Returns a single secret if found
   * @responseMessage 200 Found and retrieved secret with given ID
   * @responseMessage 404 Secret with given ID not found
   */
  @Path("{secretId}")
  @GET
  public AutomationSecretResponse readSecretById(@Auth AutomationClient automationClient,
      @PathParam("secretId") LongParam secretId) {

    List<Secret> secrets = secretController.getSecretsById(secretId.get());
    if (secrets.isEmpty()) {
      throw new NotFoundException("Secret not found.");
    }

    Secret secret = secrets.get(0);
    ImmutableList<Group> groups =
        ImmutableList.copyOf(aclDAO.getGroupsFor(secret));

    return AutomationSecretResponse.fromSecret(secret, groups);
  }

  /**
   * Deletes all versions of a secret series
   *
   * @param secretName the name of the secret series to delete
   *
   * @description Deletes all versions of a secret series.  This will delete a single secret ID.
   * @responseMessage 200 Deleted secret series
   * @responseMessage 404 Secret series not Found
   */
  @Path("{secretName}")
  @DELETE
  public Response deleteSecretSeries(@Auth AutomationClient automationClient,
      @PathParam("secretName") String secretName) {

    secretSeriesDAO.getSecretSeriesByName(secretName)
        .orElseThrow(() -> new NotFoundException("Secret series not found."));
    secretSeriesDAO.deleteSecretSeriesByName(secretName);

    return Response.ok().build();
  }
}
