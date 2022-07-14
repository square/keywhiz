package keywhiz.service.resources.automation.v2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.auth.Auth;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import keywhiz.api.model.AutomationClient;
import keywhiz.jooq.tables.records.AccessgrantsRecord;
import keywhiz.jooq.tables.records.ClientsRecord;
import keywhiz.jooq.tables.records.MembershipsRecord;
import keywhiz.jooq.tables.records.SecretsContentRecord;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.service.crypto.RowHmacGenerator;
import keywhiz.service.permissions.Action;
import keywhiz.service.permissions.PermissionCheck;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static keywhiz.jooq.Tables.ACCESSGRANTS;
import static keywhiz.jooq.Tables.CLIENTS;
import static keywhiz.jooq.Tables.MEMBERSHIPS;
import static keywhiz.jooq.Tables.SECRETS;
import static keywhiz.jooq.Tables.SECRETS_CONTENT;
import static org.jooq.impl.DSL.min;

/**
 * parentEndpointName automation/v2-backfill-row-hmac
 * resourceDescription Automation endpoints to backfill row_hmac columns
 */
@Path("/automation/v2/backfill-row-hmac")
public class BackfillRowHmacResource {
  private static final Logger logger = LoggerFactory.getLogger(BackfillRowHmacResource.class);

  private final DSLContext jooq;
  private final RowHmacGenerator rowHmacGenerator;
  private final AuditLog auditLog;
  private final PermissionCheck permissionCheck;

  @Inject
  public BackfillRowHmacResource(
      DSLContext jooq,
      RowHmacGenerator rowHmacGenerator,
      AuditLog auditLog,
      PermissionCheck permissionCheck) {
    this.jooq = jooq;
    this.rowHmacGenerator = rowHmacGenerator;
    this.auditLog = auditLog;
    this.permissionCheck = permissionCheck;
  }

  /**
   * Backfill row_hmac for a specific secret.
   */
  @Timed @ExceptionMetered
  @Path("secret/name/{secretName}")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public void backfillSecretRowHmacByName(
      @Auth AutomationClient automationClient,
      @PathParam("secretName") String secretName,
      @QueryParam("force") boolean force) {
    Optional<SecretsRecord> maybeRow = jooq.selectFrom(SECRETS)
        .where(SECRETS.NAME.eq(secretName))
        .fetchOptional();

    if (!maybeRow.isPresent()) {
      throw new NotFoundException();
    }

    SecretsRecord row = maybeRow.get();
    permissionCheck.checkAllowedOrThrow(automationClient, Action.UPDATE, row);

    String oldHmac = row.getRowHmac();

    if (oldHmac != null && !force) {
      return;
    }

    String newHmac = rowHmacGenerator.computeRowHmac(
        SECRETS.getName(),
        List.of(row.getName(), row.getId()));

    jooq.update(SECRETS)
        .set(SECRETS.ROW_HMAC, newHmac)
        .where(SECRETS.ID.eq(row.getId()))
        .execute();

    Map<String, String> extraInfo = ImmutableMap.<String, String>builder()
        .put("oldHmac", Objects.toString(oldHmac))
        .put("newHmac", newHmac)
        .build();

    auditLog.recordEvent(new Event(
        Instant.now(),
        EventTag.SECRET_BACKFILLHMAC,
        automationClient.getName(),
        secretName,
        extraInfo));
  }

  /**
   * Backfill row_hmac for this secret.
   */
  @Timed @ExceptionMetered
  @Path("backfill-secrets/{cursor_start}/{max_rows}")
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public void backfillSecretsRowHmac(
      @Auth AutomationClient automationClient,
      @PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-secrets: processing secrets");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(SECRETS.ID))
          .from(SECRETS)
          .fetch().get(0).value1() - 1;
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<SecretsRecord> rows = jooq.selectFrom(SECRETS)
          .where(SECRETS.ID.greaterThan(cursor))
          .orderBy(SECRETS.ID)
          .limit(1000)
          .fetchInto(SECRETS);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        permissionCheck.checkAllowedOrThrow(automationClient, Action.UPDATE, row);

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
  public void backfillSecretsContentRowHmac(
      @Auth AutomationClient automationClient,
      @PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-secrets-content: processing secrets content");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(SECRETS_CONTENT.ID))
          .from(SECRETS_CONTENT)
          .fetch().get(0).value1() - 1;
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<SecretsContentRecord> rows = jooq.selectFrom(SECRETS_CONTENT)
          .where(SECRETS_CONTENT.ID.greaterThan(cursor))
          .orderBy(SECRETS_CONTENT.ID)
          .limit(1000)
          .fetchInto(SECRETS_CONTENT);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        permissionCheck.checkAllowedOrThrow(automationClient, Action.UPDATE, row);

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
  public void backfillClientsRowHmac(
      @Auth AutomationClient automationClient,
      @PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-clients: processing clients");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(CLIENTS.ID))
          .from(CLIENTS)
          .fetch().get(0).value1() - 1;
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<ClientsRecord> rows = jooq.selectFrom(CLIENTS)
          .where(CLIENTS.ID.greaterThan(cursor))
          .orderBy(CLIENTS.ID)
          .limit(1000)
          .fetchInto(CLIENTS);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        permissionCheck.checkAllowedOrThrow(automationClient, Action.UPDATE, row);

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
  public void backfillMembershipsRowHmac(
      @Auth AutomationClient automationClient,
      @PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-memberships: processing memberships");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(MEMBERSHIPS.ID))
          .from(MEMBERSHIPS)
          .fetch().get(0).value1() - 1;
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<MembershipsRecord> rows = jooq.selectFrom(MEMBERSHIPS)
          .where(MEMBERSHIPS.ID.greaterThan(cursor))
          .orderBy(MEMBERSHIPS.ID)
          .limit(1000)
          .fetchInto(MEMBERSHIPS);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        permissionCheck.checkAllowedOrThrow(automationClient, Action.UPDATE, row);

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
  public void backfillAccessgrantsRowHmac(
      @Auth AutomationClient automationClient,
      @PathParam("cursor_start") Long cursorStart,
      @PathParam("max_rows") Long maxRows) {
    logger.info("backfill-accessgrants: processing accessgrants");
    long cursor;
    if (cursorStart != 0) {
      cursor = cursorStart;
    } else {
      cursor = jooq.select(min(ACCESSGRANTS.ID))
          .from(ACCESSGRANTS)
          .fetch().get(0).value1() - 1;
    }

    long processedRows = 0;

    while (processedRows < maxRows) {
      Result<AccessgrantsRecord> rows = jooq.selectFrom(ACCESSGRANTS)
          .where(ACCESSGRANTS.ID.greaterThan(cursor))
          .orderBy(ACCESSGRANTS.ID)
          .limit(1000)
          .fetchInto(ACCESSGRANTS);
      if (rows.isEmpty()) {
        break;
      }

      for (var row : rows) {
        permissionCheck.checkAllowedOrThrow(automationClient, Action.UPDATE, row);

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


