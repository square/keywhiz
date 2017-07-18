package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.auth.Auth;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import keywhiz.KeywhizConfig;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Group;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.service.config.Readonly;
import keywhiz.service.daos.GroupDAO;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import keywhiz.service.daos.SecretController;
import org.bouncycastle.openpgp.PGPException;
import org.c02e.jpgpj.Encryptor;
import org.c02e.jpgpj.FileMetadata;
import org.c02e.jpgpj.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static keywhiz.log.EventTag.GROUP_BACKUP;
import static org.c02e.jpgpj.CompressionAlgorithm.ZIP;
import static org.c02e.jpgpj.EncryptionAlgorithm.AES256;
import static org.c02e.jpgpj.FileMetadata.Format.UTF8;
import static org.c02e.jpgpj.HashingAlgorithm.Unsigned;

/**
 * @parentEndpointName automation/v2-backup
 * @resourceDescription Automation endpoints for backups
 */
@Path("/automation/v2/backup")
public class BackupResource {
  private static final Logger logger = LoggerFactory.getLogger(BackupResource.class);

  private final GroupDAO groupDAO;
  private final SecretController secretController;
  private final ObjectMapper objectMapper;
  private final KeywhizConfig config;
  private final AuditLog auditLog;

  @Inject
  public BackupResource(
      GroupDAOFactory groupDAOFactory,
      @Readonly SecretController secretController,
      ObjectMapper objectMapper,
      KeywhizConfig config,
      AuditLog auditLog) {
    this.groupDAO = groupDAOFactory.readonly();
    this.secretController = secretController;
    this.objectMapper = objectMapper;
    this.config = config;
    this.auditLog = auditLog;
  }

  /**
   * Backup all secrets for a given group. Returns an encrypted encrypted to
   * the backup key in the main configuration file. Only accessible to automation clients.
   *
   * @excludeParams automationClient
   * @param name Group name
   *
   * @return Encrypted archive
   */
  @Timed @ExceptionMetered
  @GET @Path("{key}/group/{group}")
  @Produces(APPLICATION_OCTET_STREAM)
  public byte[] backup(
      @Auth AutomationClient automationClient,
      @PathParam("group") String name,
      @PathParam("key") String key) {
    if (config.getBackupExportKey(key) == null) {
      throw new NotFoundException("Unknown key: " + key);
    }

    Optional<Group> groupOptional = groupDAO.getGroup(name);
    if (!groupOptional.isPresent()) {
      throw new NotFoundException("Unknown group: " + name);
    }

    Group group = groupOptional.get();

    // SecretDeliveryResponse is the same data a client receives when requesting a secret,
    // so it should have all the relevant information we need (including content, checksum).
    List<SecretDeliveryResponse> secrets = secretController.getSecretsForGroup(group).stream()
        .map(SecretDeliveryResponse::fromSecret)
        .collect(toList());

    String serialized;
    try {
      serialized = objectMapper.writeValueAsString(secrets);
    } catch (JsonProcessingException e) {
      // This should never happen
      logger.error("Unable to backup secrets", e);
      throw new InternalServerErrorException("Unable to backup secrets, check logs for details");
    }

    // Record all checksums of backed up/exported secrets so we can uniquely identify which
    // particular contents were returned in the response from inspection of the audit log.
    Map<String, String> auditInfo = secrets.stream()
        .collect(toMap(SecretDeliveryResponse::getName, SecretDeliveryResponse::getChecksum));

    // Record audit event
    auditLog.recordEvent(new Event(
        now(),
        GROUP_BACKUP,
        automationClient.getName(),
        group.getName(),
        auditInfo));

    // Perform encryption & return encrypted data
    try {
      Key exportKey = new Key(config.getBackupExportKey(key));

      Encryptor encryptor = new Encryptor(exportKey);
      encryptor.setEncryptionAlgorithm(AES256);
      encryptor.setSigningAlgorithm(Unsigned);
      encryptor.setCompressionAlgorithm(ZIP);

      ByteArrayInputStream plaintext = new ByteArrayInputStream(serialized.getBytes(UTF_8));
      ByteArrayOutputStream ciphertext = new ByteArrayOutputStream();

      encryptor.encrypt(plaintext, ciphertext, new FileMetadata(format("%s.json", group), UTF8));

      return ciphertext.toByteArray();
    } catch (PGPException | IOException e) {
      logger.error("Unable to backup secrets", e);
      throw new InternalServerErrorException("Unable to backup secrets, check logs for details");
    }
  }
}
