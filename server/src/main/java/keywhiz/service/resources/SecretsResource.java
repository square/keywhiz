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
import java.net.URI;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import keywhiz.api.CreateSecretRequest;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.VersionGenerator;
import keywhiz.auth.User;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretSeriesDAO;
import keywhiz.service.exceptions.ConflictException;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

/**
 * @parentEndpointName secrets-admin
 *
 * @resourcePath /admin/secrets
 * @resourceDescription Create, retrieve, and delete secrets
 */
@Path("/admin/secrets")
@Produces(MediaType.APPLICATION_JSON)
public class SecretsResource {
  private static final Logger logger = LoggerFactory.getLogger(SecretsResource.class);

  private final SecretController secretController;
  private final AclDAO aclDAO;
  private final SecretSeriesDAO secretSeriesDAO;

  @Inject
  public SecretsResource(SecretController secretController, AclDAO aclDAO,
      SecretSeriesDAO secretSeriesDAO) {
    this.secretController = secretController;
    this.aclDAO = aclDAO;
    this.secretSeriesDAO = secretSeriesDAO;
  }

  /**
   * Retrieve Secret by a specified name and version, or all Secrets name not given
   *
   * @optionalParams name
   * @param name the name of the Secret to retrieve, if provided
   * @optionalParams version
   * @param version the version of the Secret to retrieve, if provided
   *
   * @description Returns a single Secret or a set of all Secrets for this user.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and retrieved Secret(s)
   * @responseMessage 404 Secret with given name not found (if name provided)
   */
  @GET
  public Response findSecrets(@Auth User user, @DefaultValue("") @QueryParam("name") String name,
      @DefaultValue("") @QueryParam("version") String version) {
    if (name.isEmpty()) {
      return Response.ok().entity(listSecrets(user)).build();
    }
    return Response.ok().entity(retrieveSecret(user, name, version)).build();
  }

  protected List<SanitizedSecret> listSecrets(@Auth User user) {
    logger.info("User '{}' listing secrets.", user);
    return secretController.getSanitizedSecrets();
  }

  protected SanitizedSecret retrieveSecret(@Auth User user, String name, String version) {
    logger.info("User '{}' retrieving secret name={} version={}.", user, name, version);
    return sanitizedSecretFromNameAndVersion(name, version);
  }

  /**
   * Retrieve all versions for a Secret name
   *
   * @param name the name of the Secret to find all versions for
   *
   * @description Returns a list of all versions for the given secret.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and retrieved versions(s)
   * @responseMessage 400 Name not given
   */
  @Path("/versions")
  @GET
  public List<String> getVersionsForSecretName(@Auth User user,
      @DefaultValue("") @QueryParam("name") String name) {
    if (name.isEmpty()) {
      // Must supply a secret name to find versions for
      throw new BadRequestException("Must supply secret name to find versions.");
    }
    return retrieveSecretVersions(user, name);
  }

  /** Finds all versions for the specified secret name **/
  protected List<String> retrieveSecretVersions(User user, String name) {
    logger.info("User '{}' finding versions for secret '{}'.", user, name);
    return secretController.getVersionsForName(name);
  }

  /**
   * Create Secret
   *
   * @param request the JSON client request used to formulate the Secret
   *
   * @description Creates a Secret with the name from a valid secret request.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Successfully created Secret
   * @responseMessage 400 Secret with given name already exists
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createSecret(@Auth User user, @Valid CreateSecretRequest request) {

    logger.info("User '{}' creating secret '{}' {} versioning.",
        user, request.name, request.withVersion ? "with" : "without");

    Secret secret;
    try {
      SecretController.SecretBuilder builder =
          secretController.builder(request.name, request.content, user.getName());

      if (request.description != null) {
        builder.withDescription(request.description);
      }

      if (request.metadata != null) {
        builder.withMetadata(request.metadata);
      }

      if (request.withVersion) {
        builder.withVersion(VersionGenerator.now().toHex());
      }

      secret = builder.build();
    } catch (DataAccessException e) {
      logger.warn("Cannot create secret {}: {}", request.name, e);
      throw new ConflictException(format("Cannot create secret %s.", request.name));
    }

    URI uri = UriBuilder.fromResource(SecretsResource.class).path("{secretId}").build(secret.getId());

    return Response
        .created(uri)
        .entity(secretDetailResponseFromId(secret.getId()))
        .build();
  }

  /**
   * Retrieve Secret by ID
   *
   * @param secretId the ID of the secret to retrieve
   *
   * @description Returns a single Secret if found.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and retrieved Secret with given ID
   * @responseMessage 404 Secret with given ID not Found
   */
  @Path("{secretId}")
  @GET
  public SecretDetailResponse retrieveSecret(@Auth User user,
      @PathParam("secretId") LongParam secretId) {

    logger.info("User '{}' retrieving secret id={}.", user, secretId);
    return secretDetailResponseFromId(secretId.get());
  }

  /**
   * Delete Secret by ID
   *
   * @param secretId the ID of the Secret to be deleted
   *
   * @description Deletes a single Secret if found.
   * Used by Keywhiz CLI and the web ui.
   * @responseMessage 200 Found and deleted Secret with given ID
   * @responseMessage 404 Secret with given ID not Found
   */
  @Path("{secretId}")
  @DELETE
  public Response deleteSecret(@Auth User user, @PathParam("secretId") LongParam secretId) {
    logger.info("User '{}' deleting secret id={}.", user, secretId);

    List<Secret> secrets = secretController.getSecretsById(secretId.get());
    if (secrets.isEmpty()) {
      throw new NotFoundException("Secret not found.");
    }

    secretSeriesDAO.deleteSecretSeriesById(secretId.get());
    return Response.noContent().build();
  }

  private SecretDetailResponse secretDetailResponseFromId(long secretId) {
    List<Secret> secrets = secretController.getSecretsById(secretId);
    if (secrets.isEmpty()) {
      throw new NotFoundException("Secret not found.");
    }

    // TODO(justin): API change needed to return all versions.
    Secret secret = secrets.get(0);
    ImmutableList<Group> groups = ImmutableList.copyOf(aclDAO.getGroupsFor(secret));
    ImmutableList<Client> clients = ImmutableList.copyOf(aclDAO.getClientsFor(secret));
    return SecretDetailResponse.fromSecret(secret, groups, clients);
  }

  private SanitizedSecret sanitizedSecretFromNameAndVersion(String name, String version) {
    Optional<Secret> optionalSecret = secretController.getSecretByNameAndVersion(name, version);
    if (!optionalSecret.isPresent()) {
      throw new NotFoundException("Secret not found.");
    }

    Secret secret = optionalSecret.get();
    return SanitizedSecret.fromSecret(secret);
  }
}
