package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import keywhiz.KeywhizConfig;
import keywhiz.api.automation.v2.BatchCreateOrUpdateSecretsRequestV2;
import keywhiz.api.automation.v2.BatchCreateOrUpdateSecretsResponseV2;
import keywhiz.api.automation.v2.BatchMode;
import keywhiz.api.automation.v2.CreateOrUpdateSecretInfoV2;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.log.LogArguments;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.permissions.Action;
import keywhiz.service.permissions.PermissionCheck;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/automation/v2/batch")
public class BatchResource {
  private static final Logger logger = LoggerFactory.getLogger(BatchResource.class);

  private final DSLContext jooq;
  private final SecretController secretController;
  private final AclDAO aclDAO;
  private final SecretDAO secretDAO;
  private final AuditLog auditLog;
  private final PermissionCheck permissionCheck;
  private final KeywhizConfig config;

  @Inject
  public BatchResource(
      DSLContext jooq,
      SecretController secretController,
      AclDAO.AclDAOFactory aclDAOFactory,
      SecretDAO.SecretDAOFactory secretDAOFactory,
      AuditLog auditLog,
      PermissionCheck permissionCheck,
      KeywhizConfig config) {
    this.jooq = jooq;
    this.secretController = secretController;
    this.aclDAO = aclDAOFactory.readonly();
    this.secretDAO = secretDAOFactory.readwrite();
    this.auditLog = auditLog;
    this.permissionCheck = permissionCheck;
    this.config = config;
  }

  @Timed
  @ExceptionMetered
  @Path("secrets")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @LogArguments
  public BatchCreateOrUpdateSecretsResponseV2 batchCreateOrUpdateSecrets(
      @Auth AutomationClient automationClient,
      @Valid BatchCreateOrUpdateSecretsRequestV2 request) {
    permissionCheck.checkAllowedForTargetTypeOrThrow(automationClient, Action.CREATE, Secret.class);

    switch (request.batchMode()) {
      case BatchMode.ALL_OR_NONE:
        jooq.transaction(configuration -> {
          DSLContext dslContext = DSL.using(configuration);
          for (CreateOrUpdateSecretInfoV2 secret : request.secrets()) {
            createOrUpdateSecret(dslContext, automationClient, secret);
          }
        });
        break;
      case BatchMode.BEST_EFFORT:
        for (CreateOrUpdateSecretInfoV2 secret : request.secrets()) {
          try {
            jooq.transaction(configuration -> {
                DSLContext dslContext = DSL.using(configuration);
                createOrUpdateSecret(dslContext, automationClient, secret);
            });
          } catch (Exception e) {
            logger.error(String.format("Failed to create or update secret: %s", secret), e);
          }
        }
        break;
      case BatchMode.FAIL_FAST:
        for (CreateOrUpdateSecretInfoV2 secret : request.secrets()) {
          jooq.transaction(configuration -> {
            DSLContext dslContext = DSL.using(configuration);
            createOrUpdateSecret(dslContext, automationClient, secret);
          });
        }
        break;
      default:
        throw new IllegalArgumentException(
            String.format("Unknown batch mode: %s", request.batchMode()));
    }

    return BatchCreateOrUpdateSecretsResponseV2.builder()
        .build();
  }

  private void createOrUpdateSecret(
      DSLContext dslContext,
      AutomationClient automationClient,
      CreateOrUpdateSecretInfoV2 secret) {

    Optional<SecretSeriesAndContent> maybeSecretSeriesAndContent = secretDAO.getSecretByName(dslContext, secret.name());
    String secretOwner = secret.owner();
    if (maybeSecretSeriesAndContent.isPresent()) {
      permissionCheck.checkAllowedOrThrow(automationClient, Action.UPDATE, maybeSecretSeriesAndContent.get());
    } else {
      permissionCheck.checkAllowedForTargetTypeOrThrow(automationClient, Action.CREATE, Secret.class);
      secretOwner = getSecretOwnerForSecretCreation(dslContext, secretOwner, automationClient);
    }

    SecretController.SecretBuilder builder = secretController
        .builder(secret.name(), secret.content(), automationClient.getName(), secret.expiry())
        .withDescription(secret.description())
        .withMetadata(secret.metadata())
        .withType(secret.type())
        .withOwnerName(secretOwner);

    builder.createOrUpdate(dslContext);

    emitAuditLogEntry(automationClient, secret);
  }

  private void emitAuditLogEntry(AutomationClient automationClient, CreateOrUpdateSecretInfoV2 secret) {
    Map<String, String> extraInfo = new HashMap<>();
    if (secret.description() != null) {
      extraInfo.put("description", secret.description());
    }
    if (secret.metadata() != null) {
      extraInfo.put("metadata", secret.metadata().toString());
    }
    extraInfo.put("expiry", Long.toString(secret.expiry()));
    auditLog.recordEvent(
        new Event(
            Instant.now(),
            EventTag.SECRET_CREATEORUPDATE,
            automationClient.getName(),
            secret.name(),
            extraInfo));
  }

  private String getSecretOwnerForSecretCreation(DSLContext dslContext, String secretOwner, AutomationClient automationClient) {
    if (secretOwnerNotProvided(secretOwner) && shouldInferSecretOwnerUponCreation()) {
      return findClientGroup(dslContext, automationClient);
    }
    return secretOwner;
  }

  private static boolean secretOwnerNotProvided(String secretOwner) {
    return secretOwner == null || secretOwner.isEmpty();
  }

  private boolean shouldInferSecretOwnerUponCreation() {
    return config.getNewSecretOwnershipStrategy() == KeywhizConfig.NewSecretOwnershipStrategy.INFER_FROM_CLIENT;
  }

  private String findClientGroup(DSLContext dslContext, AutomationClient automationClient) {
    Set<Group> clientGroups = aclDAO.getGroupsFor(dslContext, automationClient);
    if (clientGroups.size() == 0) {
      logger.warn(String.format("Client %s does not belong to any group.", automationClient));
    } else if (clientGroups.size() == 1) {
      Group clientGroup = clientGroups.stream().findFirst().get();
      return clientGroup.getName();
    } else {
      String groups = new ArrayList<>(clientGroups).toString();
      logger.warn(String.format("Client %s belongs to more than one group: %s",
          automationClient,
          groups));
    }
    return null;
  }
}
