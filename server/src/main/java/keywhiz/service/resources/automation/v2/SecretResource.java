package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import io.dropwizard.auth.Auth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import keywhiz.api.automation.v2.CreateOrUpdateSecretRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.automation.v2.SecretDetailResponseV2;
import keywhiz.api.model.*;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.GroupDAO;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretController.SecretBuilder;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import keywhiz.service.exceptions.ConflictException;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @parentEndpointName automation/v2-secret-management
 * @resourceDescription Automation endpoints to manage secrets
 */
@Path("/automation/v2/secrets")
public class SecretResource {
  private static final Logger logger = LoggerFactory.getLogger(SecretResource.class);

  private final SecretController secretController;
  private final AclDAO aclDAO;
  private final GroupDAO groupDAO;
  private final SecretDAO secretDAO;

  @Inject public SecretResource(SecretController secretController, AclDAOFactory aclDAOFactory,
      GroupDAOFactory groupDAOFactory, SecretDAOFactory secretDAOFactory) {
    this.secretController = secretController;
    this.aclDAO = aclDAOFactory.readwrite();
    this.groupDAO = groupDAOFactory.readwrite();
    this.secretDAO = secretDAOFactory.readwrite();
  }

  /**
   * Creates a secret and assigns to given groups
   *
   * @excludeParams automationClient
   * @param request JSON request to create a secret
   *
   * @responseMessage 201 Created secret and assigned to given groups
   * @responseMessage 409 Secret already exists
   */
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  public Response createSecret(@Auth AutomationClient automationClient,
      @Valid CreateSecretRequestV2 request) {
    // allows new version, return version in resulting path
    String name = request.name();

    SecretBuilder builder = secretController
        .builder(name, request.content(), automationClient.getName(), request.expiry())
        .withDescription(request.description())
        .withMetadata(request.metadata())
        .withType(request.type());

    Secret secret;
    try {
      secret = builder.create();
    } catch (DataAccessException e) {
      logger.warn(format("Cannot create secret %s", name), e);
      throw new ConflictException(format("Cannot create secret %s.", name));
    }

    long secretId = secret.getId();
    groupsToGroupIds(request.groups())
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAO.findAndAllowAccess(secretId, groupId)));

    UriBuilder uriBuilder = UriBuilder.fromResource(SecretResource.class).path(name);

    return Response.created(uriBuilder.build()).build();
  }

  /**
   * Creates or updates (if it exists) a secret.
   *
   * @excludeParams automationClient
   * @param request JSON request to create a secret
   *
   * @responseMessage 201 Created secret and assigned to given groups
   */
  @Timed @ExceptionMetered
  @Path("{name}")
  @POST
  @Consumes(APPLICATION_JSON)
  public Response createOrUpdateSecret(@Auth AutomationClient automationClient,
                                       @PathParam("name") String name,
                                       @Valid CreateOrUpdateSecretRequestV2 request) {
    SecretBuilder builder = secretController
        .builder(name, request.content(), automationClient.getName(), request.expiry())
        .withDescription(request.description())
        .withMetadata(request.metadata())
        .withType(request.type());

    builder.createOrUpdate();

    UriBuilder uriBuilder = UriBuilder.fromResource(SecretResource.class).path(name);

    return Response.created(uriBuilder.build()).build();
  }

  /**
   * Retrieve listing of secrets and metadata
   *
   * @excludeParams automationClient
   * @responseMessage 200 List of secrets and metadata
   */
  @Timed @ExceptionMetered
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<String> secretListing(@Auth AutomationClient automationClient) {
    return secretController.getSanitizedSecrets(null, null).stream()
        .map(SanitizedSecret::name)
        .collect(toSet());
  }

  /**
   * Retrieve listing of secrets expiring soon
   *
   * @excludeParams automationClient
   * @param time timestamp for farthest expiry to include
   *
   * @responseMessage 200 List of secrets expiring soon
   */
  @Timed @ExceptionMetered
  @Path("expiring/{time}")
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<String> secretListingExpiring(@Auth AutomationClient automationClient, @PathParam("time") Long time) {
    List<SanitizedSecret> secrets = secretController.getSanitizedSecrets(time, null);
    return secrets.stream()
        .map(SanitizedSecret::name)
        .collect(toSet());
  }

  /**
   * Retrieve listing of secrets expiring soon in a group
   *
   * @excludeParams automationClient
   * @param time timestamp for farthest expiry to include
   * @param name Group name
   *
   * @responseMessage 200 List of secrets expiring soon in group
   */
  @Timed @ExceptionMetered
  @Path("expiring/{time}/{name}")
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<String> secretListingExpiringForGroup(@Auth AutomationClient automationClient,
      @PathParam("time") Long time, @PathParam("name") String name) {
    Group group = groupDAO.getGroup(name).orElseThrow(NotFoundException::new);

    List<SanitizedSecret> secrets = secretController.getSanitizedSecrets(time, group);
    return secrets.stream()
        .map(SanitizedSecret::name)
        .collect(toSet());
  }

  /**
   * Retrieve information on a secret series
   *
   * @excludeParams automationClient
   * @param name Secret series name
   *
   * @responseMessage 200 Secret series information retrieved
   * @responseMessage 404 Secret series not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("{name}")
  @Produces(APPLICATION_JSON)
  public SecretDetailResponseV2 secretInfo(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    SecretSeriesAndContent secret = secretDAO.getSecretByName(name)
        .orElseThrow(NotFoundException::new);
    return SecretDetailResponseV2.builder()
        .series(secret.series())
        .expiry(secret.content().expiry())
        .build();
  }

  /**
   * Retrieve the given range of versions of this secret, sorted from newest to
   * oldest update time.  If versionIdx is nonzero, then numVersions versions,
   * starting from versionIdx in the list and increasing in index, will be
   * returned (set numVersions to a very large number to retrieve all versions).
   * For instance, versionIdx = 5 and numVersions = 10 will retrieve entries
   * at indices 5 through 14.
   *
   * @param name Secret series name
   * @param versionIdx The index in the list of versions of the first version to retrieve
   * @param numVersions The number of versions to retrieve
   * @excludeParams automationClient
   * @responseMessage 200 Secret series information retrieved
   * @responseMessage 404 Secret series not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("{name}/versions/{versionIdx}-{numVersions}")
  @Produces(APPLICATION_JSON)
  public Iterable<SecretDetailResponseV2> secretVersions(@Auth AutomationClient automationClient,
      @PathParam("name") String name, @PathParam("versionIdx") int versionIdx,
      @PathParam("numVersions") int numVersions) {
    ImmutableList<SecretVersion> versions =
        secretDAO.getSecretVersionsByName(name, versionIdx, numVersions)
            .orElseThrow(NotFoundException::new);

    return versions.stream()
        .map(v -> SecretDetailResponseV2.builder()
            .secretVersion(v)
            .build())
        .collect(toList());
  }


  /**
   * Reset the current version of the given secret to the given version index.
   *
   * @param name Secret series name
   * @param versionId The desired current version
   * @excludeParams automationClient
   * @responseMessage 200 Secret series current version updated successfully
   * @responseMessage 400 Invalid secret version specified
   * @responseMessage 404 Secret series not found
   */
  @Timed @ExceptionMetered
  @Path("{name}/setversion/{versionId}")
  @POST
  public Response resetSecretVersion(@Auth AutomationClient automationClient,
      @PathParam("name") String name, @PathParam("versionId") long versionId) {
    secretDAO.setCurrentSecretVersionByName(name, versionId);

    // If the secret wasn't found or the request was misformed, setCurrentSecretVersionByName
    // already threw an exception
    return Response.status(Response.Status.OK).build();
  }

  /**
   * Listing of groups a secret is assigned to
   *
   * @excludeParams automationClient
   * @param name Secret series name
   *
   * @responseMessage 200 Listing succeeded
   * @responseMessage 404 Secret series not found
   */
  @Timed @ExceptionMetered
  @GET
  @Path("{name}/groups")
  @Produces(APPLICATION_JSON)
  public Iterable<String> secretGroupsListing(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    // TODO: Use latest version instead of non-versioned
    Secret secret = secretController.getSecretByName(name)
        .orElseThrow(NotFoundException::new);
    return aclDAO.getGroupsFor(secret).stream()
        .map(Group::getName)
        .collect(toSet());
  }

  /**
   * Modify the groups a secret is assigned to
   *
   * @excludeParams automationClient
   * @param name Secret series name
   * @param request JSON request to modify groups
   *
   * @responseMessage 201 Group membership changed
   * @responseMessage 404 Secret series not found
   */
  @Timed @ExceptionMetered
  @PUT
  @Path("{name}/groups")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public Iterable<String> modifySecretGroups(@Auth AutomationClient automationClient,
      @PathParam("name") String name, @Valid ModifyGroupsRequestV2 request) {
    // TODO: Use latest version instead of non-versioned
    Secret secret = secretController.getSecretByName(name)
        .orElseThrow(NotFoundException::new);

    long secretId = secret.getId();
    Set<String> oldGroups = aclDAO.getGroupsFor(secret).stream()
        .map(Group::getName)
        .collect(toSet());

    Set<String> groupsToAdd = Sets.difference(request.addGroups(), oldGroups);
    Set<String> groupsToRemove = Sets.intersection(request.removeGroups(), oldGroups);

    // TODO: should optimize AclDAO to use names and return only name column

    groupsToGroupIds(groupsToAdd)
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAO.findAndAllowAccess(secretId, groupId)));

    groupsToGroupIds(groupsToRemove)
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAO.findAndRevokeAccess(secretId, groupId)));

    return aclDAO.getGroupsFor(secret).stream()
        .map(Group::getName)
        .collect(toSet());
  }

  /**
   * Delete a secret series
   *
   * @excludeParams automationClient
   * @param name Secret series name
   *
   * @responseMessage 204 Secret series deleted
   * @responseMessage 404 Secret series not found
   */
  @Timed @ExceptionMetered
  @DELETE
  @Path("{name}")
  public Response deleteSecretSeries(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    secretDAO.getSecretByName(name)
        .orElseThrow(NotFoundException::new);
    secretDAO.deleteSecretsByName(name);
    return Response.noContent().build();
  }

  private Stream<Optional<Long>> groupsToGroupIds(Set<String> groupNames) {
    return groupNames.stream()
        .map(groupDAO::getGroup)
        .map((group) -> group.map(Group::getId));
  }
}
