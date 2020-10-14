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

package keywhiz.service.resources.admin;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.params.LongParam;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.automation.v2.CreateOrUpdateSecretRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.PartialUpdateSecretRequestV2;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.auth.User;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import keywhiz.service.exceptions.ConflictException;
import org.apache.http.HttpStatus;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * parentEndpointName secrets-admin
 * <p>
 * resourcePath /admin/secrets
 * <p>
 * resourceDescription Create, retrieve, and delete secrets
 */
@Path("/admin/secrets")
@Produces(APPLICATION_JSON)
public class SecretsResource {
  private static final Logger logger = LoggerFactory.getLogger(SecretsResource.class);

  private final SecretController secretController;
  private final AclDAO aclDAOReadOnly;
  private final SecretDAO secretDAOReadWrite;
  private final SecretDAO secretDAOReadOnly;
  private final AuditLog auditLog;

  @SuppressWarnings("unused")
  @Inject public SecretsResource(SecretController secretController, AclDAOFactory aclDAOFactory,
      SecretDAOFactory secretDAOFactory, AuditLog auditLog) {
    this.secretController = secretController;
    this.aclDAOReadOnly = aclDAOFactory.readonly();
    this.secretDAOReadWrite = secretDAOFactory.readwrite();
    this.secretDAOReadOnly = secretDAOFactory.readonly();
    this.auditLog = auditLog;
  }

  /**
   * Constructor for testing
   */
  @VisibleForTesting SecretsResource(SecretController secretController, AclDAO aclDAOReadOnly,
      SecretDAO secretDAOReadWrite, AuditLog auditLog) {
    this.secretController = secretController;
    this.aclDAOReadOnly = aclDAOReadOnly;
    this.secretDAOReadWrite = secretDAOReadWrite;
    this.secretDAOReadOnly = secretDAOReadWrite;
    this.auditLog = auditLog;
  }

  /**
   * Retrieve Secret by a specified name and version, or all Secrets if name is not given
   *
   * @param user        the admin user performing this operation
   * @param name        the name of the Secret to retrieve, if provided
   * @param nameOnly    if set, the result only contains the id and name for the secrets.
   * @param idx         if set, the desired starting index in a list of secrets to be retrieved
   * @param num         if set, the number of secrets to retrieve
   * @param newestFirst whether to order the secrets by creation date with newest first; defaults to
   *                    true
   * @return a single Secret or a set of all Secrets for this user.
   * <p>
   * Used by Keywhiz CLI and the web ui.
   * <p>
   * responseMessage 200 Found and retrieved Secret(s)
   * <p>
   * responseMessage 404 Secret with given name not found (if name provided)
   */
  @Timed @ExceptionMetered
  @GET
  public Response findSecrets(@Auth User user, @DefaultValue("") @QueryParam("name") String name,
      @DefaultValue("") @QueryParam("nameOnly") String nameOnly, @QueryParam("idx") Integer idx,
      @QueryParam("num") Integer num,
      @DefaultValue("true") @QueryParam("newestFirst") Boolean newestFirst) {
    if (!name.isEmpty() && idx != null && num != null) {
      throw new BadRequestException("Name and idx/num cannot both be specified");
    }

    validateArguments(name, nameOnly, idx, num);

    if (name.isEmpty()) {
      if (nameOnly.isEmpty()) {
        if (idx == null || num == null) {
          return Response.ok().entity(listSecrets(user)).build();
        } else {
          return Response.ok().entity(listSecretsBatched(user, idx, num, newestFirst)).build();
        }
      } else {
        return Response.ok().entity(listSecretsNameOnly(user)).build();
      }
    }
    return Response.ok().entity(retrieveSecret(user, name)).build();
  }

  private void validateArguments(String name, String nameOnly, Integer idx, Integer num) {
    if ((idx == null && num != null) || (idx != null && num == null)) {
      throw new IllegalArgumentException("Both idx and num must be specified");
    }
    if (!name.isEmpty() && idx != null && num != null) {
      throw new IllegalArgumentException("Name, idx, and num must not all be specified");
    }
    if (nameOnly.isEmpty() && idx != null && num != null) {
      throw new IllegalArgumentException(
          "nameOnly option is not valid for batched secret retrieval");
    }
  }

  protected List<SanitizedSecret> listSecrets(@Auth User user) {
    logger.info("User '{}' listing secrets.", user);
    return secretController.getSanitizedSecrets(null, null);
  }

  protected List<SanitizedSecret> listSecretsNameOnly(@Auth User user) {
    logger.info("User '{}' listing secrets.", user);
    return secretController.getSecretsNameOnly();
  }

  protected List<SanitizedSecret> listSecretsBatched(@Auth User user, int idx, int num,
      boolean newestFirst) {
    logger.info("User '{}' listing secrets with idx '{}', num '{}', newestFirst '{}'.", user, idx,
        num, newestFirst);
    return secretController.getSecretsBatched(idx, num, newestFirst);
  }

  protected SanitizedSecret retrieveSecret(@Auth User user, String name) {
    logger.info("User '{}' retrieving secret name={}.", user, name);
    return sanitizedSecretFromName(name);
  }

  /**
   * Create Secret
   *
   * @param user    the admin user performing this operation
   * @param request the JSON client request used to formulate the Secret
   * @return 201 on success, 400 if a secret with the given name already exists
   * <p>
   * description Creates a Secret with the name from a valid secret request. Used by Keywhiz CLI and
   * the web ui.
   * <p>
   * responseMessage 201 Successfully created Secret
   * <p>
   * responseMessage 400 Secret with given name already exists
   */
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public Response createSecret(@Auth User user, @Valid CreateSecretRequestV2 request) {

    logger.info("User '{}' creating secret '{}'.", user, request.name());

    Secret secret;
    try {
      SecretController.SecretBuilder builder =
          secretController.builder(request.name(), request.content(), user.getName(),
              request.expiry());

      if (request.description() != null) {
        builder.withDescription(request.description());
      }

      if (request.metadata() != null) {
        builder.withMetadata(request.metadata());
      }

      secret = builder.create();
    } catch (DataAccessException e) {
      logger.info(format("Cannot create secret %s", request.name()), e);
      throw new ConflictException(format("Cannot create secret %s.", request.name()));
    }

    URI uri =
        UriBuilder.fromResource(SecretsResource.class).path("{secretId}").build(secret.getId());
    Response response = Response
        .created(uri)
        .entity(secretDetailResponseFromId(secret.getId()))
        .build();

    if (response.getStatus() == HttpStatus.SC_CREATED) {
      Map<String, String> extraInfo = new HashMap<>();
      if (request.description() != null) {
        extraInfo.put("description", request.description());
      }
      if (request.metadata() != null) {
        extraInfo.put("metadata", request.metadata().toString());
      }
      extraInfo.put("expiry", Long.toString(request.expiry()));
      auditLog.recordEvent(
          new Event(Instant.now(), EventTag.SECRET_CREATE, user.getName(), request.name(),
              extraInfo));
    }
    // TODO (jessep): Should we also log failures?

    return response;
  }

  /**
   * Create or update secret
   *
   * @param user    the admin user performing this operation
   * @param request the JSON client request used to formulate the Secret
   * @return 201 when secret created or updated
   * <p>
   * responseMessage 201 Successfully created or updated Secret
   */
  @Path("{name}")
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public Response createOrUpdateSecret(@Auth User user, @PathParam("name") String secretName,
      @Valid CreateOrUpdateSecretRequestV2 request) {

    logger.info("User '{}' createOrUpdate secret '{}'.", user, secretName);

    Secret secret = secretController
        .builder(secretName, request.content(), user.getName(), request.expiry())
        .withDescription(request.description())
        .withMetadata(request.metadata())
        .withType(request.type())
        .createOrUpdate();

    URI uri = UriBuilder.fromResource(SecretsResource.class).path(secretName).build();

    Response response =
        Response.created(uri).entity(secretDetailResponseFromId(secret.getId())).build();

    if (response.getStatus() == HttpStatus.SC_CREATED) {
      Map<String, String> extraInfo = new HashMap<>();
      if (request.description() != null && !request.description().isEmpty()) {
        extraInfo.put("description", request.description());
      }
      if (request.metadata() != null && !request.metadata().isEmpty()) {
        extraInfo.put("metadata", request.metadata().toString());
      }
      extraInfo.put("expiry", Long.toString(request.expiry()));
      auditLog.recordEvent(
          new Event(Instant.now(), EventTag.SECRET_CREATEORUPDATE, user.getName(), secretName,
              extraInfo));
    }
    return response;
  }

  /**
   * Update a subset of the fields of an existing secret
   *
   * @param user    the admin user performing this operation
   * @param request the JSON client request used to formulate the Secret
   * @return 201 when update successful
   * <p>
   * responseMessage 201 Successfully updated Secret
   */
  @Path("{name}/partialupdate")
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public Response partialUpdateSecret(@Auth User user, @PathParam("name") String secretName, @Valid
      PartialUpdateSecretRequestV2 request) {

    logger.info("User '{}' partialUpdate secret '{}'.", user, secretName);

    long id = secretDAOReadWrite.partialUpdateSecret(secretName, user.getName(), request);

    URI uri = UriBuilder.fromResource(SecretsResource.class)
        .path(secretName)
        .path("partialupdate")
        .build();

    Response response = Response.created(uri).entity(secretDetailResponseFromId(id)).build();

    if (response.getStatus() == HttpStatus.SC_CREATED) {
      Map<String, String> extraInfo = new HashMap<>();
      if (request.descriptionPresent()) {
        extraInfo.put("description", request.description());
      }
      if (request.metadataPresent()) {
        extraInfo.put("metadata", request.metadata().toString());
      }
      if (request.expiryPresent()) {
        extraInfo.put("expiry", Long.toString(request.expiry()));
      }
      auditLog.recordEvent(
          new Event(Instant.now(), EventTag.SECRET_UPDATE, user.getName(), secretName, extraInfo));
    }
    return response;
  }

  /**
   * Retrieve Secret by ID
   *
   * @param user     the admin user performing this operation
   * @param secretId the ID of the secret to retrieve
   * @return the specified secret, if found
   * <p>
   * description Returns a single Secret if found. Used by Keywhiz CLI and the web ui.
   * <p>
   * responseMessage 200 Found and retrieved Secret with given ID
   * <p>
   * responseMessage 404 Secret with given ID not Found
   */
  @Path("{secretId}")
  @Timed @ExceptionMetered
  @GET
  public SecretDetailResponse retrieveSecret(@Auth User user,
      @PathParam("secretId") LongParam secretId) {

    logger.info("User '{}' retrieving secret id={}.", user, secretId);
    return secretDetailResponseFromId(secretId.get());
  }

  /**
   * Retrieve the given range of versions of this secret, sorted from newest to oldest update time.
   * If versionIdx is nonzero, then numVersions versions, starting from versionIdx in the list and
   * increasing in index, will be returned (set numVersions to a very large number to retrieve all
   * versions). For instance, versionIdx = 5 and numVersions = 10 will retrieve entries at indices 5
   * through 14.
   *
   * @param user        the admin user performing this operation
   * @param name        Secret series name
   * @param versionIdx  The index in the list of versions of the first version to retrieve
   * @param numVersions The number of versions to retrieve
   * @return a list of a secret's versions, if found
   * <p>
   * responseMessage 200 Secret series information retrieved
   * <p>
   * responseMessage 404 Secret series not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("versions/{name}")
  @Produces(APPLICATION_JSON)
  public List<SanitizedSecret> secretVersions(@Auth User user,
      @PathParam("name") String name, @QueryParam("versionIdx") int versionIdx,
      @QueryParam("numVersions") int numVersions) {

    logger.info("User '{}' listing {} versions starting at index {} for secret '{}'.", user,
        numVersions, versionIdx, name);

    ImmutableList<SanitizedSecret> versions =
        secretDAOReadOnly.getSecretVersionsByName(name, versionIdx, numVersions)
            .orElseThrow(NotFoundException::new);

    return versions;
  }

  /**
   * Rollback to a previous secret version
   *
   * @param user       the admin user performing this operation
   * @param secretName the name of the secret to rollback
   * @param versionId  the ID of the version to return to
   * @return 200 if the rollback was successful, 404 for missing secret or bad input
   * <p>
   * description Returns the previous versions of the secret if found Used by Keywhiz CLI.
   * <p>
   * responseMessage 200 Found and reset the secret to this version
   * <p>
   * responseMessage 404 Secret with given name not found or invalid version provided
   */
  @Path("rollback/{secretName}/{versionId}")
  @Timed @ExceptionMetered
  @POST
  public Response resetSecretVersion(@Auth User user, @PathParam("secretName") String secretName,
      @PathParam("versionId") LongParam versionId) {

    logger.info("User '{}' rolling back secret '{}' to version with ID '{}'.", user, secretName,
        versionId);

    secretDAOReadWrite.setCurrentSecretVersionByName(secretName, versionId.get(), user.getName());

    // If the secret wasn't found or the request was misformed, setCurrentSecretVersionByName
    // already threw an exception
    Map<String, String> extraInfo = new HashMap<>();
    extraInfo.put("new version", versionId.toString());
    auditLog.recordEvent(
        new Event(Instant.now(), EventTag.SECRET_CHANGEVERSION, user.getName(), secretName,
            extraInfo));

    // Send the new secret in response
    URI uri = UriBuilder.fromResource(SecretsResource.class)
        .path("rollback/{secretName}/{versionID}")
        .build(secretName, versionId);
    return Response.created(uri).entity(secretDetailResponseFromName(secretName)).build();
  }

  /**
   * Delete Secret by ID
   *
   * @param user     the admin user performing this operation
   * @param secretId the ID of the Secret to be deleted
   * @return 200 if secret deleted, 404 if not found
   * <p>
   * description Deletes a single Secret if found. Used by Keywhiz CLI and the web ui.
   * <p>
   * responseMessage 200 Found and deleted Secret with given ID
   * <p>
   * responseMessage 404 Secret with given ID not Found
   */
  @Path("{secretId}")
  @Timed @ExceptionMetered
  @DELETE
  public Response deleteSecret(@Auth User user, @PathParam("secretId") LongParam secretId) {
    Optional<Secret> secret = secretController.getSecretById(secretId.get());
    if (!secret.isPresent()) {
      logger.info("User '{}' tried deleting a secret which was not found (id={})", user,
          secretId.get());
      throw new NotFoundException("Secret not found.");
    }

    logger.info("User '{}' deleting secret id={}, name='{}'", user, secretId,
        secret.get().getName());

    // Get the groups for this secret, so they can be restored manually if necessary
    Set<String> groups =
        aclDAOReadOnly.getGroupsFor(secret.get()).stream().map(Group::getName).collect(toSet());

    secretDAOReadWrite.deleteSecretsByName(secret.get().getName());

    // Record the deletion
    Map<String, String> extraInfo = new HashMap<>();
    extraInfo.put("groups", groups.toString());
    extraInfo.put("current version", secret.get().getVersion().toString());
    auditLog.recordEvent(
        new Event(Instant.now(), EventTag.SECRET_DELETE, user.getName(), secret.get().getName(),
            extraInfo));
    return Response.noContent().build();
  }

  /**
   * Finds all deleted secrets that have the queried name, which will be queried as "." + name + ".%"
   *
   * @param user        the admin user performing this operation
   * @param name        Secret series deleted name
   * @return a list of deleted secrets that have the name
   * <p>
   * responseMessage 200 Secret series information retrieved
   * <p>
   * responseMessage 404 Secret series not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("deleted/{name}")
  @Produces(APPLICATION_JSON)
  public List<SecretSeries> findDeletedSecretsByName(@Auth User user,
      @PathParam("name") String name) {

    logger.info("User '{}' finding deleted secrets with name '{}'.", user, name);

    return secretDAOReadOnly.getSecretsWithDeletedName(name);
  }

  /**
   * Rename Secret by ID to the given name
   *
   * @param user     the admin user performing this operation
   * @param secretId the ID of the Secret to be deleted
   * @return 200 if secret deleted, 404 if not found
   * <p>
   * description Deletes a single Secret if found. Used by Keywhiz CLI and the web ui.
   * <p>
   * responseMessage 200 Found and deleted Secret with given ID
   * <p>
   * responseMessage 404 Secret with given ID not Found
   */
  @Path("rename/{secretId}/{secretName}")
  @Timed @ExceptionMetered
  @POST
  public Response renameSecret(@Auth User user, @PathParam("secretId") LongParam secretId,
      @PathParam("secretName") String secretName) {
    Optional<Secret> secret = secretController.getSecretByName(secretName);
    if (secret.isPresent()) {
      logger.info("User '{}' tried renaming a secret, but another secret with that name "
              + "already exists (name={})", user, secretId.get());
      throw new NotAllowedException("That name is already taken by another secret"); //use different error
    }

    logger.info("User '{}' renamed secret id={} to name='{}'", user, secretId,
        secretName);

    secretDAOReadWrite.renameSecretById(secretId.get(), secretName);

    // Record the rename
    Map<String, String> extraInfo = new HashMap<>();
    auditLog.recordEvent(
        new Event(Instant.now(), EventTag.SECRET_RENAME, user.getName(), secretName,
            extraInfo));
    return Response.noContent().build();
  }

  /**
   * Retrieve all Secrets Contents associated with the given Secret ID
   *
   * @param user     the admin user performing this operation
   * @param secretId the ID of the Secret that will be associated with the secrets contents returned
   * @return 200 if secret deleted, 404 if not found
   * <p>
   * description Returns a list of secrets contents for a given Secret ID.
   * <p>
   * responseMessage 200 Found and deleted Secret with given ID
   * <p>
   * responseMessage 404 Secret with given ID not Found
   */
  @Path("secretcontents/list/{secretId}")
  @Timed @ExceptionMetered
  @POST
  public List<SecretSeriesAndContent> getSecretsContentsBySecretId(@Auth User user,
      @PathParam("secretId") LongParam secretId, @QueryParam("idx") Integer idx,
      @QueryParam("num") Integer num) {

    ImmutableList<SecretSeriesAndContent> secretsContents =
        secretDAOReadOnly.getDeletedSecretVersionsBySecretId(secretId.get(), idx, num)
            .orElseThrow(NotFoundException::new);

    logger.info("User '{}' retrieving secrets contents for secretId={} from idx={} for {} secrets contents.",
        user, secretId, idx, num);

    return secretsContents;
  }

  /**
   * Update Secret content for the given secret (identified by secretId) to secretsContentId
   *
   * @param user     the admin user performing this operation
   * @param secretId the ID of the Secret to be updated
   * @param secretsContentId, the Secret content to be updated
   * @return 200 if secret deleted, 404 if not found
   * <p>
   * description Updates a secret current for a given secret. Used by Keywhiz CLI and the web ui.
   * <p>
   * responseMessage 200 Found and deleted Secret with given ID
   * <p>
   * responseMessage 404 Secret with given ID not Found
   */
  @Path("secretcontents/update/{secretsContentId}/{secretId}")
  @Timed @ExceptionMetered
  @POST
  public Response updateSecretsCurrent(@Auth User user, @PathParam("secretId") LongParam secretId,
      @PathParam("secretsContentId") LongParam secretsContentId) {
    Optional<Secret> secret = secretController.getSecretById(secretId.get());
    if (!secret.isPresent()) {
      logger.info("User '{}' tried updating current for a secret which was not found (id={})", user,
          secretId.get());
      throw new NotFoundException("Secret not found.");
    }

    logger.info("User '{}' updated current id={} for secret id={}", user, secretId);

    secretDAOReadWrite.updateSecretsCurrent(secretId.get(), secretsContentId.get());

    // Record the rename
    Map<String, String> extraInfo = new HashMap<>();
    auditLog.recordEvent(
        new Event(Instant.now(), EventTag.SECRET_UPDATECURRENT, user.getName(), secret.get().getName(),
            extraInfo));
    return Response.noContent().build();
  }

  private SecretDetailResponse secretDetailResponseFromId(long secretId) {
    Optional<Secret> secrets = secretController.getSecretById(secretId);
    if (secrets.isEmpty()) {
      throw new NotFoundException("Secret not found.");
    }

    ImmutableList<Group> groups = ImmutableList.copyOf(aclDAOReadOnly.getGroupsFor(secrets.get()));
    ImmutableList<Client> clients =
        ImmutableList.copyOf(aclDAOReadOnly.getClientsFor(secrets.get()));
    return SecretDetailResponse.fromSecret(secrets.get(), groups, clients);
  }

  private SecretDetailResponse secretDetailResponseFromName(String secretName) {
    Optional<Secret> secrets = secretController.getSecretByName(secretName);
    if (secrets.isEmpty()) {
      throw new NotFoundException("Secret not found.");
    }

    ImmutableList<Group> groups = ImmutableList.copyOf(aclDAOReadOnly.getGroupsFor(secrets.get()));
    ImmutableList<Client> clients =
        ImmutableList.copyOf(aclDAOReadOnly.getClientsFor(secrets.get()));
    return SecretDetailResponse.fromSecret(secrets.get(), groups, clients);
  }

  private SanitizedSecret sanitizedSecretFromName(String name) {
    Optional<Secret> optionalSecret = secretController.getSecretByName(name);
    if (optionalSecret.isEmpty()) {
      throw new NotFoundException("Secret not found.");
    }

    Secret secret = optionalSecret.get();
    return SanitizedSecret.fromSecret(secret);
  }
}
