package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import io.dropwizard.auth.Auth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.jooq.tables.Secrets;
import keywhiz.jooq.tables.records.AccessgrantsRecord;
import keywhiz.jooq.tables.records.ClientsRecord;
import keywhiz.jooq.tables.records.MembershipsRecord;
import keywhiz.jooq.tables.records.SecretsContentRecord;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.service.crypto.RowHmacGenerator;
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
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static keywhiz.jooq.Tables.ACCESSGRANTS;
import static keywhiz.jooq.Tables.CLIENTS;
import static keywhiz.jooq.Tables.MEMBERSHIPS;
import static keywhiz.jooq.Tables.SECRETS;
import static keywhiz.jooq.Tables.SECRETS_CONTENT;
import static org.jooq.impl.DSL.log;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;

/**
 * @parentEndpointName automation/v2-backfill-row-hmac
 * @resourceDescription Automation endpoints to backfill row_hmac columns
 */
@Path("/automation/v2/backfill-row-hmac")
public class BackfillRowHmacResource {
  private static final Logger logger = LoggerFactory.getLogger(BackfillRowHmacResource.class);

  private final DSLContext jooq;
  private final RowHmacGenerator rowHmacGenerator;

  @Inject
  public BackfillRowHmacResource(DSLContext jooq, RowHmacGenerator rowHmacGenerator) {
    this.jooq = jooq;
    this.rowHmacGenerator = rowHmacGenerator;
  }

  /**
   * Backfill row_hmac for this secret.
   */
  @Timed @ExceptionMetered
  @Path("backfill-secrets/{cursor_start}/{max_rows}")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public void backfillSecretsRowHmac(@PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-secrets: processing secrets");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(SECRETS.ID))
          .from(SECRETS)
          .fetch().get(0).value1();
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<SecretsRecord> rows = jooq.selectFrom(SECRETS)
          .where(SECRETS.ID.greaterOrEqual(cursor))
          .orderBy(SECRETS.ID)
          .limit(1000)
          .fetchInto(SECRETS);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        cursor = row.getId();
        if (!row.getRowHmac().isEmpty()) {
          continue;
        }

        String rowHmac = rowHmacGenerator.computeRowHmac(SECRETS.getName(),
            List.of(row.getName(), row.getId()));
        jooq.update(SECRETS)
            .set(SECRETS.ROW_HMAC, rowHmac)
            .where(SECRETS.ID.eq(row.getId()))
            .execute();

        processedRows += 1;
      }

      logger.info("backfill-secrets: updating from {} with {} rows processed",
          cursor, processedRows);
    }
  }

  /**
   * Backfill row_hmac for this secrets content.
   */
  @Timed @ExceptionMetered
  @Path("backfill-secrets-content/{cursor_start}/{max_rows}")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public void backfillSecretsContentRowHmac(@PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-secrets-content: processing secrets content");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(SECRETS_CONTENT.ID))
          .from(SECRETS_CONTENT)
          .fetch().get(0).value1();
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<SecretsContentRecord> rows = jooq.selectFrom(SECRETS_CONTENT)
          .where(SECRETS_CONTENT.ID.greaterOrEqual(cursor))
          .orderBy(SECRETS_CONTENT.ID)
          .limit(1000)
          .fetchInto(SECRETS_CONTENT);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        cursor = row.getId();

        String rowHmac = rowHmacGenerator.computeRowHmac(SECRETS_CONTENT.getName(),
            List.of(row.getEncryptedContent(), row.getMetadata(), row.getId()));
        jooq.update(SECRETS_CONTENT)
            .set(SECRETS_CONTENT.ROW_HMAC, rowHmac)
            .where(SECRETS_CONTENT.ID.eq(row.getId()))
            .execute();

        processedRows += 1;
      }

      logger.info("backfill-secrets-content: updating from {} with {} rows processed",
          cursor, processedRows);
    }
  }

  /**
   * Backfill row_hmac for this client.
   */
  @Timed @ExceptionMetered
  @Path("backfill-clients/{cursor_start}/{max_rows}")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public void backfillClientsRowHmac(@PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-clients: processing clients");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(CLIENTS.ID))
          .from(CLIENTS)
          .fetch().get(0).value1();
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<ClientsRecord> rows = jooq.selectFrom(CLIENTS)
          .where(CLIENTS.ID.greaterOrEqual(cursor))
          .orderBy(CLIENTS.ID)
          .limit(1000)
          .fetchInto(CLIENTS);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        cursor = row.getId();

        String rowHmac = rowHmacGenerator.computeRowHmac(CLIENTS.getName(),
            List.of(row.getName(), row.getId()));
        jooq.update(CLIENTS)
            .set(CLIENTS.ROW_HMAC, rowHmac)
            .where(CLIENTS.ID.eq(row.getId()))
            .execute();

        processedRows += 1;
      }

      logger.info("backfill-clients: updating from {} with {} rows processed",
          cursor, processedRows);
    }
  }

  /**
   * Backfill row_hmac for this membership id.
   */
  @Timed @ExceptionMetered
  @Path("backfill-memberships/{cursor_start}/{max_rows}")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public void backfillMembershipsRowHmac(@PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-memberships: processing memberships");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(MEMBERSHIPS.ID))
          .from(MEMBERSHIPS)
          .fetch().get(0).value1();
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<MembershipsRecord> rows = jooq.selectFrom(MEMBERSHIPS)
          .where(MEMBERSHIPS.ID.greaterOrEqual(cursor))
          .orderBy(MEMBERSHIPS.ID)
          .limit(1000)
          .fetchInto(MEMBERSHIPS);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        cursor = row.getId();

        String rowHmac = rowHmacGenerator.computeRowHmac(MEMBERSHIPS.getName(),
            List.of(row.getClientid(), row.getGroupid()));
        jooq.update(MEMBERSHIPS)
            .set(MEMBERSHIPS.ROW_HMAC, rowHmac)
            .where(MEMBERSHIPS.ID.eq(row.getId()))
            .execute();

        processedRows += 1;
      }

      logger.info("backfill-memberships: updating from {} with {} rows processed",
          cursor, processedRows);
    }
  }

  /**
   * Backfill accessgrants for this membership id.
   */
  @Timed @ExceptionMetered
  @Path("backfill-accessgrants/{cursor_start}/{max_rows}")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public void backfillAccessgrantsRowHmac(@PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-accessgrants: processing accessgrants");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(ACCESSGRANTS.ID))
          .from(ACCESSGRANTS)
          .fetch().get(0).value1();
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<AccessgrantsRecord> rows = jooq.selectFrom(ACCESSGRANTS)
          .where(ACCESSGRANTS.ID.greaterOrEqual(cursor))
          .orderBy(ACCESSGRANTS.ID)
          .limit(1000)
          .fetchInto(ACCESSGRANTS);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        cursor = row.getId();

        String rowHmac = rowHmacGenerator.computeRowHmac(ACCESSGRANTS.getName(),
            List.of(row.getGroupid(), row.getSecretid()));
        jooq.update(ACCESSGRANTS)
            .set(ACCESSGRANTS.ROW_HMAC, rowHmac)
            .where(ACCESSGRANTS.ID.eq(row.getId()))
            .execute();

        processedRows += 1;
      }
      logger.info("backfill-accessgrants: updating from {} with {} rows processed",
          cursor, processedRows);
    }
  }
}


