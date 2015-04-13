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
import com.google.inject.Inject;
import io.dropwizard.auth.Auth;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import keywhiz.api.CreateSecretRequest;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;
import keywhiz.api.model.VersionGenerator;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretSeriesDAO;
import keywhiz.service.exceptions.ConflictException;
import org.skife.jdbi.v2.exceptions.StatementException;

import static java.lang.String.format;

/**
 * @parentEndpointName versioned-secrets
 *
 * @resourcePath /v2/secrets
 * @resourceDescription Create, retrieve, and remove versioned secrets
 */
@Path("/v2/secrets")
@Produces(MediaType.APPLICATION_JSON)
public class VersionedSecretResource {
  private final SecretController secretController;
  private final AclDAO aclDAO;
  private final SecretSeriesDAO secretSeriesDAO;
  private final SecretDAO secretDAO;

  private final String LATEST_VERSION = "LATEST";

  @Inject
  public VersionedSecretResource(SecretController secretController, AclDAO aclDAO,
      SecretSeriesDAO secretSeriesDAO, SecretDAO secretDAO) {
    this.secretController = secretController;
    this.aclDAO = aclDAO;
    this.secretSeriesDAO = secretSeriesDAO;
    this.secretDAO = secretDAO;
  }

  /**
   * Retrieve all secret names.  Note that this could be an expensive query
   *
   * @description Returns a set of all secret names
   * @responseMessage 200 Found and retrieved secrets
   */
  @GET
  public Response readAllSecrets(@Auth AutomationClient automationClient) {
    List<String> secretNames = secretSeriesDAO.getSecretSeries()
        .stream()
        .map(s -> s.getName())
        .collect(Collectors.toList());

    return Response.ok().entity(secretNames).build();
  }

  /**
   * Retrieve all versions for a secretName
   *
   * @param secretName the name of the secret to retrieve versions for
   * @description Returns a list of versions for the specified secret name
   * @responseMessage 200 Found and retrieved versions for given secret name
   * @responseMessage 404 Secret with given name not found
   */
  @Path("{secretName}")
  @GET
  public Response readSecretById(@Auth AutomationClient automationClient,
      @PathParam("secretName") String secretName) {
    List<String> versions = secretController.getVersionsForName(secretName);
    if (versions.isEmpty()) {
      throw new NotFoundException("Secret not found.");
    }
    return Response.ok().entity(versions).build();
  }

  /**
   * Retrieve a secret by name and version
   *
   * @param secretName the name of the secret to retrieve
   * @param version the version of secretName to retrieve.  "LATEST" will retrieve the most recent version
   * @description Returns a single secret if found
   * @responseMessage 200 Found and retrieved secret with given name and version
   * @responseMessage 404 Secret with given name and version not found
   */
  @Path("{secretName}/{version}")
  @GET
  public SecretDetailResponse readSecretByNameAndVersion(@Auth AutomationClient automationClient,
      @PathParam("secretName") String secretName,
      @PathParam("version") String version) {
    if (version.equals(LATEST_VERSION)) {
      version = secretController.getLatestVersion(secretName);
    }

    Optional<Secret> secret = secretController.getSecretByNameAndVersion(secretName, version);
    if (!secret.isPresent()) {
      throw new NotFoundException("Secret not found.");
    }

    ImmutableList<Group> groups = ImmutableList.copyOf(aclDAO.getGroupsFor(secret.get()));
    ImmutableList<Client> clients = ImmutableList.copyOf(aclDAO.getClientsFor(secret.get()));
    return SecretDetailResponse.fromSecret(secret.get(), groups, clients);
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

  /**
   * Deletes a specific version of a secret
   *
   * @param secretName the name of the secret to delete
   * @param version the version of the secret to delete
   *
   * @description Deletes a single version of a secret.  Other versions may exist post-deletion.
   * @responseMessage 200 Deleted secret version.
   * @responseMessage 404 Secret series or version not Found
   */
  @Path("{secretName}/{version}")
  @DELETE
  public Response deleteSecretVersion(@Auth AutomationClient automationClient,
      @PathParam("secretName") String secretName,
      @PathParam("version") String version) {

    secretSeriesDAO.getSecretSeriesByName(secretName)
        .orElseThrow(() -> new NotFoundException("Secret series not found."));

    secretDAO.deleteSecretByNameAndVersion(secretName, version);

    return Response.ok().build();
  }

  /**
   * Create versioned secret
   *
   * @param request the JSON client request used to formulate the secret
   *
   * @description Creates a versioned secret with the name from a valid secret request.
   * If a secret series already exists, simply adds a new version.
   * @responseMessage 200 Successfully created Secret
   * @responseMessage 400 Secret with given name already exists
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createSecret(@Auth AutomationClient automationClient,
      @Valid CreateSecretRequest request) {

    Secret secret;
    String version;
    try {
      SecretController.SecretBuilder builder =
          secretController.builder(request.name, request.content, automationClient.getName());

      if (request.description != null) {
        builder.withDescription(request.description);
      }

      if (request.metadata != null) {
        builder.withMetadata(request.metadata);
      }

      // Always creates a versioned secret on this endpoint
      version = VersionGenerator.now().toHex();
      builder.withVersion(version);

      secret = builder.build();
    } catch (StatementException e) {
      throw new ConflictException(format("Cannot create secret %s.", request.name));
    }

    URI uri = UriBuilder.fromResource(VersionedSecretResource.class).path("{secretName}/{version}")
        .build(secret.getName(), secret.getVersion());

    Optional<Secret> createdSecret = secretController
        .getSecretByNameAndVersion(secret.getName(), secret.getVersion());

    if (!createdSecret.isPresent()) {
      throw new NotFoundException("Secret creation unsuccessful.");
    }

    ImmutableList<Group> groups = ImmutableList.copyOf(aclDAO.getGroupsFor(createdSecret.get()));
    ImmutableList<Client> clients = ImmutableList.copyOf(aclDAO.getClientsFor(createdSecret.get()));

    return Response
        .created(uri)
        .entity(SecretDetailResponse.fromSecret(createdSecret.get(), groups, clients))
        .build();
  }
}
