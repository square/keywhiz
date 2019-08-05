package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.AclDAO.AclDAOFactory;
import keywhiz.service.daos.ClientDAO;
import keywhiz.service.daos.ClientDAO.ClientDAOFactory;
import keywhiz.service.daos.SecretContentDAO;
import keywhiz.service.daos.SecretContentDAO.SecretContentDAOFactory;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import keywhiz.service.daos.SecretSeriesDAO;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @parentEndpointName automation/v2-backfill-row-hmac
 * @resourceDescription Automation endpoints to backfill row_hmac columns
 */
@Path("/automation/v2/backfill-row-hmac")
public class BackfillRowHmacResource {
  private static final Logger logger = LoggerFactory.getLogger(BackfillRowHmacResource.class);

  private final AclDAO aclDAO;
  private final ClientDAO clientDAO;
  private final SecretDAO secretDAO;
  private final SecretContentDAO secretContentDAO;
  private final SecretSeriesDAO secretSeriesDAO;

  @Inject
  public BackfillRowHmacResource(AclDAOFactory aclDAOFactory,
      ClientDAOFactory clientDAOFactory, SecretDAOFactory secretDAOFactory,
      SecretContentDAOFactory secretContentDAOFactory,
      SecretSeriesDAOFactory secretSeriesDAOFactory) {
    this.aclDAO = aclDAOFactory.readwrite();
    this.clientDAO = clientDAOFactory.readwrite();
    this.secretDAO = secretDAOFactory.readwrite();
    this.secretContentDAO = secretContentDAOFactory.readwrite();
    this.secretSeriesDAO = secretSeriesDAOFactory.readwrite();
  }

  /**
   * Backfill row_hmac for this secret.
   */
  @Timed @ExceptionMetered
  @Path("{secretIdFrom}/{secretIdTo}/backfill-secrets")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public boolean backfillSecretsRowHmac(@Auth AutomationClient automationClient,
      @PathParam("secretIdFrom") Long secretIdFrom, @PathParam("secretIdTo") Long secretIdTo) {
    int modifiedRows = 0;
    logger.info("backfill-secrets: processing secrets from {} to {}", secretIdFrom, secretIdTo);
    for (long secretId = secretIdFrom; secretId < secretIdTo; secretId++) {
      Optional<SecretSeriesAndContent> secret = secretDAO.getSecretById(secretId);

      if (secret.isEmpty()) {
        return false;
      }

      SecretSeries secretSeries = secret.get().series();
      int resultRows = secretSeriesDAO.setSecretsRowHmac(secretSeries);
      if (resultRows != 1) {
        logger.info("backfill-secrets {}: unexpectedly modified {} rows", secretId, resultRows);
      }
      modifiedRows += resultRows;
    }

    return modifiedRows == secretIdTo - secretIdFrom;
  }

  /**
   * Backfill row_hmac for this secrets content.
   */
  @Timed @ExceptionMetered
  @Path("{secretContentIdFrom}/{secretContentIdTo}/backfill-secrets-content")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public boolean backfillSecretsContentRowHmac(@Auth AutomationClient automationClient,
      @PathParam("secretContentIdFrom") Long secretContentIdFrom,
      @PathParam("secretContentIdTo") Long secretContentIdTo) {
    int modifiedRows = 0;
    logger.info("backfill-secrets-content: processing secrets content from {} to {}",
        secretContentIdFrom, secretContentIdTo);
    for (long secretContentId = secretContentIdFrom; secretContentId < secretContentIdTo; secretContentId++) {
      Optional<SecretContent> secretContent =
          secretContentDAO.getSecretContentById(secretContentId);

      if (secretContent.isEmpty()) {
        return false;
      }

      int resultRows = secretContentDAO.setRowHmac(secretContent.get());
      if (resultRows != 1) {
        logger.info("backfill-secrets-content {}: unexpectedly modified {} rows",
            secretContentId, resultRows);
      }
      modifiedRows += resultRows;
    }

    return modifiedRows == secretContentIdTo - secretContentIdFrom;
  }

  /**
   * Backfill row_hmac for this client.
   */
  @Timed @ExceptionMetered
  @Path("{clientIdFrom}/{clientIdTo}/backfill-clients")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public boolean backfillClientsRowHmac(@Auth AutomationClient automationClient,
      @PathParam("clientIdFrom") Long clientIdFrom, @PathParam("clientIdTo") Long clientIdTo) {
    int modifiedRows = 0;
    logger.info("backfill-clients: processing clients from {} to {}",
        clientIdFrom, clientIdTo);
    for (long clientId = clientIdFrom; clientId < clientIdTo; clientId++) {
      Optional<Client> client = clientDAO.getClientById(clientId);

      if (client.isEmpty()) {
        return false;
      }

      int resultRows = clientDAO.setRowHmac(client.get());
      if (resultRows != 1) {
        logger.info("backfill-clients {}: unexpectedly modified {} rows", clientId, resultRows);
      }
      modifiedRows += resultRows;
    }

    return modifiedRows == clientIdTo - clientIdFrom;
  }

  /**
   * Backfill row_hmac for this membership id.
   */
  @Timed @ExceptionMetered
  @Path("{membershipIdFrom}/{membershipIdTo}/backfill-memberships")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public boolean backfillMembershipsRowHmac(@Auth AutomationClient automationClient,
      @PathParam("membershipIdFrom") Long membershipIdFrom,
      @PathParam("membershipIdTo") Long membershipIdTo) {
    int modifiedRows = 0;
    logger.info("backfill-memberships: processing memberships from {} to {}",
        membershipIdFrom, membershipIdTo);
    for (long membershipId = membershipIdFrom; membershipId < membershipIdTo; membershipId++) {
      int resultRows = aclDAO.setMembershipsRowHmac(membershipId);
      if (resultRows != 1) {
        logger.info("backfill-memberships {}: unexpectedly modified {} rows", membershipId,
            resultRows);
      }
      modifiedRows += resultRows;
    }

    return modifiedRows == membershipIdTo - membershipIdFrom;
  }

  /**
   * Backfill accessgrants for this membership id.
   */
  @Timed @ExceptionMetered
  @Path("{accessgrantIdFrom}/{accessgrantIdTo}/backfill-accessgrants")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public boolean backfillAccessgrantsRowHmac(@Auth AutomationClient automationClient,
      @PathParam("accessgrantIdFrom") Long accessgrantIdFrom,
      @PathParam("accessgrantIdTo") Long accessgrantIdTo) {
    int modifiedRows = 0;
    logger.info("backfill-accessgrants: processing accessgrants from {} to {}",
        accessgrantIdFrom, accessgrantIdTo);
    for (long accessgrantId = accessgrantIdFrom; accessgrantId < accessgrantIdTo; accessgrantId++) {
      int resultRows = aclDAO.setAccessgrantsRowHmac(accessgrantId);
      if (resultRows != 1) {
        logger.info("backfill-accessgrants {}: unexpectedly modified {} rows",
            accessgrantId, resultRows);
      }
      modifiedRows += resultRows;
    }

    return modifiedRows == accessgrantIdTo - accessgrantIdFrom;
  }
}


