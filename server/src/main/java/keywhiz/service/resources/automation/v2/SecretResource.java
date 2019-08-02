package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import io.dropwizard.auth.Auth;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import keywhiz.api.automation.v2.CreateOrUpdateSecretRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.automation.v2.PartialUpdateSecretRequestV2;
import keywhiz.api.automation.v2.SecretContentsRequestV2;
import keywhiz.api.automation.v2.SecretContentsResponseV2;
import keywhiz.api.automation.v2.SecretDetailResponseV2;
import keywhiz.api.automation.v2.SetSecretVersionRequestV2;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SanitizedSecretWithGroups;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.ContentCryptographer;
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
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.HOURS;
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
  private final AuditLog auditLog;
  private final SecretSeriesDAO secretSeriesDAO;
  private final ContentCryptographer cryptographer;
  private final SecretController secretControllerReadOnly;

  @Inject public SecretResource(SecretController secretController, AclDAOFactory aclDAOFactory,
      GroupDAOFactory groupDAOFactory, SecretDAOFactory secretDAOFactory, AuditLog auditLog,
      SecretSeriesDAOFactory secretSeriesDAOFactory, ContentCryptographer cryptographer,
      @Readonly SecretController secretControllerReadOnly) {
    this.secretController = secretController;
    this.aclDAO = aclDAOFactory.readwrite();
    this.groupDAO = groupDAOFactory.readwrite();
    this.secretDAO = secretDAOFactory.readwrite();
    this.auditLog = auditLog;
    this.secretSeriesDAO = secretSeriesDAOFactory.readwrite();
    this.cryptographer = cryptographer;
    this.secretControllerReadOnly = secretControllerReadOnly;
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
    String user = automationClient.getName();

    SecretBuilder builder = secretController
        .builder(name, request.content(), automationClient.getName(), request.expiry())
        .withDescription(request.description())
        .withMetadata(request.metadata())
        .withType(request.type());

    Secret secret;
    try {
      secret = builder.create();
    } catch (DataAccessException e) {
      logger.info(format("Cannot create secret %s", name), e);
      throw new ConflictException(format("Cannot create secret %s.", name));
    }

    Map<String, String> extraInfo = new HashMap<>();
    if (request.description() != null) {
      extraInfo.put("description", request.description());
    }
    if (request.metadata() != null) {
      extraInfo.put("metadata", request.metadata().toString());
    }
    extraInfo.put("expiry", Long.toString(request.expiry()));
    auditLog.recordEvent(new Event(Instant.now(), EventTag.SECRET_CREATE, user, name, extraInfo));

    long secretId = secret.getId();
    groupsToGroupIds(request.groups())
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAO.findAndAllowAccess(secretId, groupId, auditLog, user, new HashMap<>())));

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

    Map<String, String> extraInfo = new HashMap<>();
    if (request.description() != null) {
      extraInfo.put("description", request.description());
    }
    if (request.metadata() != null) {
      extraInfo.put("metadata", request.metadata().toString());
    }
    extraInfo.put("expiry", Long.toString(request.expiry()));
    auditLog.recordEvent(new Event(Instant.now(), EventTag.SECRET_CREATEORUPDATE, automationClient.getName(), name, extraInfo));

    UriBuilder uriBuilder = UriBuilder.fromResource(SecretResource.class).path(name);

    return Response.created(uriBuilder.build()).build();
  }

  /**
   * Updates a subset of the fields of an existing secret
   *
   * @excludeParams automationClient
   * @param request JSON request to update a secret
   *
   * @responseMessage 201 Created secret and assigned to given groups
   */
  @Timed @ExceptionMetered
  @Path("{name}/partialupdate")
  @POST
  @Consumes(APPLICATION_JSON)
  public Response partialUpdateSecret(@Auth AutomationClient automationClient,
      @PathParam("name") String name,
      @Valid PartialUpdateSecretRequestV2 request) {
    secretDAO.partialUpdateSecret(name, automationClient.getName(), request);

    Map<String, String> extraInfo = new HashMap<>();
    if (request.description() != null) {
      extraInfo.put("description", request.description());
    }
    if (request.metadata() != null) {
      extraInfo.put("metadata", request.metadata().toString());
    }
    if (request.expiry() != null) {
      extraInfo.put("expiry", Long.toString(request.expiry()));
    }
    auditLog.recordEvent(new Event(Instant.now(), EventTag.SECRET_UPDATE, automationClient.getName(), name, extraInfo));

    UriBuilder uriBuilder = UriBuilder.fromResource(SecretResource.class).path(name);

    return Response.created(uriBuilder.build()).build();
  }

  /**
   * Retrieve listing of secret names.  If "idx" and "num" are both provided, retrieve "num"
   * names starting at "idx" from a list of secret names ordered by creation date, with
   * order depending on "newestFirst" (which defaults to "true")
   *
   * @excludeParams automationClient
   * @param idx the index from which to start retrieval in the list of secret names
   * @param num the number of names to retrieve
   * @param newestFirst whether to list the most-recently-created names first
   * @responseMessage 200 List of secret names
   * @responseMessage 400 Invalid (negative) idx or num
   */
  @Timed @ExceptionMetered
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<String> secretListing(@Auth AutomationClient automationClient,
      @QueryParam("idx") Integer idx, @QueryParam("num") Integer num,
      @DefaultValue("true") @QueryParam("newestFirst") boolean newestFirst) {
    if (idx != null && num != null) {
      if (idx < 0 || num < 0) {
        throw new BadRequestException(
            "Index and num must both be positive when retrieving batched secrets!");
      }
      return secretControllerReadOnly.getSecretsBatched(idx, num, newestFirst).stream()
          .map(SanitizedSecret::name)
          .collect(toList());
    }
    return secretControllerReadOnly.getSanitizedSecrets(null, null).stream()
        .map(SanitizedSecret::name)
        .collect(toSet());
  }

  /**
   * Retrieve listing of secrets.  If "idx" and "num" are both provided, retrieve "num"
   * names starting at "idx" from a list of secrets ordered by creation date, with
   * order depending on "newestFirst" (which defaults to "true")
   *
   * @excludeParams automationClient
   * @param idx the index from which to start retrieval in the list of secrets
   * @param num the number of names to retrieve
   * @param newestFirst whether to list the most-recently-created names first
   * @responseMessage 200 List of secret names
   * @responseMessage 400 Invalid (negative) idx or num
   */
  @Timed @ExceptionMetered
  @Path("/v2")
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<SanitizedSecret> secretListingV2(@Auth AutomationClient automationClient,
      @QueryParam("idx") Integer idx, @QueryParam("num") Integer num,
      @DefaultValue("true") @QueryParam("newestFirst") boolean newestFirst) {
    if (idx != null && num != null) {
      if (idx < 0 || num < 0) {
        throw new BadRequestException(
            "Index and num must both be positive when retrieving batched secrets!");
      }
      return secretControllerReadOnly.getSecretsBatched(idx, num, newestFirst);
    }
    return secretControllerReadOnly.getSanitizedSecrets(null, null);
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
    List<SanitizedSecret> secrets = secretControllerReadOnly.getSanitizedSecrets(time, null);
    return secrets.stream()
        .map(SanitizedSecret::name)
        .collect(toList());
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
  @Path("expiring/v2/{time}")
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<SanitizedSecret> secretListingExpiringV2(@Auth AutomationClient automationClient, @PathParam("time") Long time) {
    List<SanitizedSecret> secrets = secretControllerReadOnly.getSanitizedSecrets(time, null);
    return secrets;
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
  @Path("expiring/v3/{time}")
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<SanitizedSecretWithGroups> secretListingExpiringV3(@Auth AutomationClient automationClient, @PathParam("time") Long time) {
    List<SanitizedSecretWithGroups> secrets = secretControllerReadOnly.getExpiringSanitizedSecrets(time);
    return secrets;
  }

  /**
   * Backfill expiration for this secret.
   */
  @Timed @ExceptionMetered
  @Path("{name}/backfill-expiration")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public boolean backfillExpiration(@Auth AutomationClient automationClient, @PathParam("name") String name, List<String> passwords) {
    Optional<Secret> secretOptional = secretController.getSecretByName(name);
    if (!secretOptional.isPresent()) {
      throw new NotFoundException("No such secret: " + name);
    }

    Secret secret = secretOptional.get();
    Optional<Instant> existingExpiry = Optional.empty();
    if (secret.getExpiry() > 0) {
      existingExpiry = Optional.of(Instant.ofEpochMilli(secret.getExpiry()*1000));
    }

    String secretName = secret.getName();
    byte[] secretContent = Base64.getDecoder().decode(secret.getSecret());

    // Always try empty password
    passwords.add("");

    Instant expiry = null;
    if (secretName.endsWith(".crt") || secretName.endsWith(".pem") || secretName.endsWith(".key")) {
      expiry = ExpirationExtractor.expirationFromEncodedCertificateChain(secretContent);
    } else if (secretName.endsWith(".gpg") || secretName.endsWith(".pgp")) {
      expiry = ExpirationExtractor.expirationFromOpenPGP(secretContent);
    } else if (secretName.endsWith(".p12") || secretName.endsWith(".pfx")) {
      while (expiry == null && !passwords.isEmpty()) {
        String password = passwords.remove(0);
        expiry = ExpirationExtractor.expirationFromKeystore("PKCS12", password, secretContent);
      }
    } else if (secretName.endsWith(".jceks")) {
      while (expiry == null && !passwords.isEmpty()) {
        String password = passwords.remove(0);
        expiry = ExpirationExtractor.expirationFromKeystore("JCEKS", password, secretContent);
      }
    } else if (secretName.endsWith(".jks")) {
      while (expiry == null && !passwords.isEmpty()) {
        String password = passwords.remove(0);
        expiry = ExpirationExtractor.expirationFromKeystore("JKS", password, secretContent);
      }
    }

    if (expiry != null) {
      if (existingExpiry.isPresent()) {
        long offset = existingExpiry.get().until(expiry, HOURS);
        if (offset > 24 || offset < -24) {
          logger.warn(
              "Extracted expiration of secret {} differs from actual by more than {} hours (extracted = {}, database = {}).",
              secretName, offset, expiry, existingExpiry.get());
        }

        // Do not overwrite existing expiry, we just want to check for differences and warn.
        return true;
      }

      logger.info("Found expiry for secret {}: {}", secretName, expiry.getEpochSecond());
      boolean success = secretDAO.setExpiration(name, expiry);
      if (success) {
        Map<String, String> extraInfo = new HashMap<>();
        extraInfo.put("backfilled expiry", Long.toString(expiry.getEpochSecond()));
        auditLog.recordEvent(new Event(Instant.now(), EventTag.SECRET_BACKFILLEXPIRY, automationClient.getName(), name, extraInfo));
      }
      return success;
    }

    logger.info("Unable to determine expiry for secret {}", secretName);
    return false;
  }

  /**
   * Backfill content hmac for this secret.
   */
  @Timed @ExceptionMetered
  @Path("{name}/backfill-hmac")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public boolean backfillHmac(@Auth AutomationClient automationClient, @PathParam("name") String name, List<String> passwords) {
    Optional<SecretSeriesAndContent> secret = secretDAO.getSecretByName(name);

    if (!secret.isPresent()) {
      return false;
    }
    logger.info("backfill-hmac {}: processing secret", name);

    SecretContent secretContent = secret.get().content();
    if (!secretContent.hmac().isEmpty()) {
      return true; // No need to backfill
    }
    String hmac = cryptographer.computeHmac(cryptographer.decrypt(secretContent.encryptedContent()).getBytes(UTF_8), "hmackey");
    return secretSeriesDAO.setHmac(secretContent.id(), hmac) == 1; // We expect only one row to be changed
  }

  /**
   * Retrieve listing of secrets expiring soon in a group
   *
   * @excludeParams automationClient
   * @param time timestamp for farthest expiry to include
   * @param name Group name
   * @responseMessage 200 List of secrets expiring soon in group
   */
  @Timed @ExceptionMetered
  @Path("expiring/{time}/{name}")
  @GET
  @Produces(APPLICATION_JSON)
  public Iterable<String> secretListingExpiringForGroup(@Auth AutomationClient automationClient,
      @PathParam("time") Long time, @PathParam("name") String name) {
    Group group = groupDAO.getGroup(name).orElseThrow(NotFoundException::new);

    List<SanitizedSecret> secrets = secretControllerReadOnly.getSanitizedSecrets(time, group);
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
        .seriesAndContent(secret)
        .build();
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
  @Path("{name}/sanitized")
  @Produces(APPLICATION_JSON)
  public SanitizedSecret getSanitizedSecret(@Auth AutomationClient automationClient,
      @PathParam("name") String name) {
    return SanitizedSecret.fromSecretSeriesAndContent(
        secretDAO.getSecretByName(name).orElseThrow(NotFoundException::new));
  }

  /**
   * Retrieve contents for a set of secret series.  Throws an exception
   * for unexpected errors (i. e. empty secret names or errors connecting to
   * the database); returns a response containing the contents of found
   * secrets and a list of any missing secrets.
   *
   * @excludeParams automationClient
   *
   * @responseMessage 200 Secret series information retrieved
   */
  @Timed @ExceptionMetered
  @POST
  @Path("request/contents")
  @Produces(APPLICATION_JSON)
  public SecretContentsResponseV2 secretContents(@Auth AutomationClient automationClient,
      @Valid SecretContentsRequestV2 request) {
    HashMap<String, String> successSecrets = new HashMap<>();
    ArrayList<String> missingSecrets = new ArrayList<>();

    // Get the contents for each secret, recording any errors
    for (String secretName : request.secrets()) {
      // Get the secret, if present
      Optional<Secret> secret = secretController.getSecretByName(secretName);

      if (!secret.isPresent()) {
        missingSecrets.add(secretName);
      } else {
        successSecrets.put(secretName, secret.get().getSecret());
      }
    }

    // Record the read in the audit log, tracking which secrets were found and not found
    Map<String, String> extraInfo = new HashMap<>();
    extraInfo.put("success_secrets", successSecrets.keySet().toString());
    extraInfo.put("missing_secrets", missingSecrets.toString());
    auditLog.recordEvent(new Event(Instant.now(), EventTag.SECRET_READCONTENT, automationClient.getName(), request.secrets().toString(), extraInfo));

    return SecretContentsResponseV2.builder()
        .successSecrets(successSecrets)
        .missingSecrets(missingSecrets)
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
  @Path("{name}/versions")
  @Produces(APPLICATION_JSON)
  public Iterable<SecretDetailResponseV2> secretVersions(@Auth AutomationClient automationClient,
      @PathParam("name") String name, @QueryParam("versionIdx") int versionIdx,
      @QueryParam("numVersions") int numVersions) {
    ImmutableList<SanitizedSecret> versions =
        secretDAO.getSecretVersionsByName(name, versionIdx, numVersions)
            .orElseThrow(NotFoundException::new);

    return versions.stream()
        .map(v -> SecretDetailResponseV2.builder()
            .sanitizedSecret(v)
            .build())
        .collect(toList());
  }


  /**
   * Reset the current version of the given secret to the given version index.
   *
   * @param request A request to update a given secret
   * @excludeParams automationClient
   * @responseMessage 201 Secret series current version updated successfully
   * @responseMessage 400 Invalid secret version specified
   * @responseMessage 404 Secret series not found
   */
  @Timed @ExceptionMetered
  @Path("{name}/setversion")
  @POST
  public Response resetSecretVersion(@Auth AutomationClient automationClient,
      @Valid SetSecretVersionRequestV2 request) {
    secretDAO.setCurrentSecretVersionByName(request.name(), request.version(),
        automationClient.getName());

    // If the secret wasn't found or the request was misformed, setCurrentSecretVersionByName
    // already threw an exception
    Map<String, String> extraInfo = new HashMap<>();
    extraInfo.put("new version", Long.toString(request.version()));
    auditLog.recordEvent(new Event(Instant.now(), EventTag.SECRET_CHANGEVERSION,
        automationClient.getName(), request.name(), extraInfo));

    return Response.status(Response.Status.CREATED).build();
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
    Secret secret = secretControllerReadOnly.getSecretByName(name)
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
    String user = automationClient.getName();

    long secretId = secret.getId();
    Set<String> oldGroups = aclDAO.getGroupsFor(secret).stream()
        .map(Group::getName)
        .collect(toSet());

    Set<String> groupsToAdd = Sets.difference(request.addGroups(), oldGroups);
    Set<String> groupsToRemove = Sets.intersection(request.removeGroups(), oldGroups);

    // TODO: should optimize AclDAO to use names and return only name column

    groupsToGroupIds(groupsToAdd)
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAO.findAndAllowAccess(secretId, groupId, auditLog, user, new HashMap<>())));

    groupsToGroupIds(groupsToRemove)
        .forEach((maybeGroupId) -> maybeGroupId.ifPresent(
            (groupId) -> aclDAO.findAndRevokeAccess(secretId, groupId, auditLog, user, new HashMap<>())));

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
    Secret secret = secretController.getSecretByName(name).orElseThrow(() -> new NotFoundException("Secret series not found."));

    // Get the groups for this secret so they can be restored manually if necessary
    Set<String> groups = aclDAO.getGroupsFor(secret).stream().map(Group::getName).collect(toSet());

    secretDAO.deleteSecretsByName(name);

    // Record the deletion in the audit log
    Map<String, String> extraInfo = new HashMap<>();
    extraInfo.put("groups", groups.toString());
    extraInfo.put("current version", secret.getVersion().toString());
    auditLog.recordEvent(new Event(Instant.now(), EventTag.SECRET_DELETE, automationClient.getName(), name, extraInfo));
    return Response.noContent().build();
  }

  private Stream<Optional<Long>> groupsToGroupIds(Set<String> groupNames) {
    return groupNames.stream()
        .map(groupDAO::getGroup)
        .map((group) -> group.map(Group::getId));
  }
}
