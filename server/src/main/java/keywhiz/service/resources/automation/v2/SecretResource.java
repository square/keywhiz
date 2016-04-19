package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
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
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.automation.v2.SecretDetailResponseV2;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.VersionGenerator;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.GroupDAO;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretController.SecretBuilder;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import keywhiz.service.daos.SecretSeriesDAO;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import keywhiz.service.exceptions.ConflictException;
import org.apache.commons.lang3.NotImplementedException;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
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
  private final SecretSeriesDAO secretSeriesDAO;

  @Inject public SecretResource(SecretController secretController, AclDAOFactory aclDAOFactory,
      GroupDAOFactory groupDAOFactory, SecretDAOFactory secretDAOFactory,
      SecretSeriesDAOFactory secretSeriesDAOFactory) {
    this.secretController = secretController;
    this.aclDAO = aclDAOFactory.readwrite();
    this.groupDAO = groupDAOFactory.readwrite();
    this.secretDAO = secretDAOFactory.readwrite();
    this.secretSeriesDAO = secretSeriesDAOFactory.readwrite();
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
  @POST @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Consumes(APPLICATION_JSON)
  public Response createSecret(@Auth AutomationClient automationClient,
      @Valid CreateSecretRequestV2 request) {
    // allows new version, return version in resulting path
    String name = request.name();

    SecretBuilder builder = secretController
        .builder(name, request.content(), automationClient.getName())
        .withDescription(request.description())
        .withMetadata(request.metadata())
        .withType(request.type());

    if (request.versioned()) {
      builder.withVersion(VersionGenerator.now().toHex());
    }

    Secret secret;
    try {
      secret = builder.build();
    } catch (DataAccessException e) {
      logger.warn("Cannot create secret {}: {}", name, e);
      throw new ConflictException(format("Cannot create secret %s.", name));
    }

    long secretId = secret.getId();
    groupsToGroupIds(request.groups())
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAO.findAndAllowAccess(secretId, groupId)));

    UriBuilder uriBuilder = UriBuilder.fromResource(SecretResource.class).path(name);

    if (request.versioned()) {
      uriBuilder.path(secret.getVersion());
    }

    return Response.created(uriBuilder.build()).build();
  }

  /**
   * Retrieve listing of secret names
   *
   * @excludeParams automationClient
   * @responseMessage 200 List of secret names
   */
  @GET @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Produces(APPLICATION_JSON)
  public Iterable<String> secretListing(@Auth AutomationClient automationClient) {
    return secretController.getSanitizedSecrets().stream()
        .map(SanitizedSecret::name)
        .collect(toSet());
  }

  /**
   * Modify a secret series
   *
   * @excludeParams automationClient
   *
   * @responseMessage 201 Secret series modified successfully
   * @responseMessage 404 Secret series not found
   */
  @POST @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Path("{name}")
  @Produces(APPLICATION_JSON)
  public SecretDetailResponseV2 modifySecretSeries(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    SecretSeries secret = secretSeriesDAO.getSecretSeriesByName(name)
        .orElseThrow(NotFoundException::new);
    // TODO: DAO to mutate metadata, name
    throw new NotImplementedException("Need to implement mutation methods in DAO for secret " +
        secret.name());
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
  @GET @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Path("{name}")
  @Produces(APPLICATION_JSON)
  public SecretDetailResponseV2 secretInfo(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    SecretSeries secret = secretSeriesDAO.getSecretSeriesByName(name)
        .orElseThrow(NotFoundException::new);
    List<String> versions = secretController.getVersionsForName(name);
    return SecretDetailResponseV2.builder()
        .series(secret)
        .versions(versions)
        .build();
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
  @GET @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Path("{name}/groups")
  public Iterable<String> secretGroupsListing(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    // TODO: Use latest version instead of non-versioned
    Secret secret = secretController.getSecretByNameAndVersion(name, "")
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
  @PUT @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Path("{name}/groups")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public Iterable<String> modifySecretGroups(@Auth AutomationClient automationClient,
      @PathParam("name") String name, @Valid ModifyGroupsRequestV2 request) {
    // TODO: Use latest version instead of non-versioned
    Secret secret = secretController.getSecretByNameAndVersion(name, "")
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
   * Retrieve information on a version of a secret
   *
   * @excludeParams automationClient
   * @param name Secret series name
   * @param version Secret version, or empty
   *
   * @responseMessage 200 Secret information retrieved
   * @responseMessage 404 Secret not found
   */
  @GET @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Path("{name}/{version:.*}")
  @Produces(APPLICATION_JSON)
  public SecretDetailResponseV2 secretVersionInfo(@Auth AutomationClient automationClient,
      @PathParam("name") String name, @PathParam("version") String version) {
    Secret secret = secretController.getSecretByNameAndVersion(name, version)
        .orElseThrow(NotFoundException::new);
    return SecretDetailResponseV2.builder().secret(secret).build();
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
  @DELETE @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Path("{name}")
  public Response deleteSecretSeries(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    secretSeriesDAO.getSecretSeriesByName(name)
        .orElseThrow(NotFoundException::new);
    secretDAO.deleteSecretsByName(name);
    return Response.noContent().build();
  }

  /**
   * Delete a version of a secret
   *
   * @excludeParams automationClient
   * @param name Secret series name
   * @param version Secret version, or empty
   *
   * @responseMessage 204 Secret version deleted
   * @responseMessage 404 Secret version not found
   */
  @DELETE @Timed @Metered(name="QPS") @ExceptionMetered(name="ExceptionQPS")
  @Path("{name}/{version:.*}")
  public Response deleteSecretVersion(@Auth AutomationClient automationClient,
      @PathParam("name") String name, @PathParam("version") String version) {
    secretController.getSecretByNameAndVersion(name, version)
        .orElseThrow(NotFoundException::new);
    secretDAO.deleteSecretByNameAndVersion(name, version);
    return Response.noContent().build();
  }

  private Stream<Optional<Long>> groupsToGroupIds(Set<String> groupNames) {
    return groupNames.stream()
        .map(groupDAO::getGroup)
        .map((group) -> group.map(Group::getId));
  }
}
