package keywhiz.service.resources.automation.v2;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import keywhiz.api.ApiDate;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.log.AuditLog;
import keywhiz.log.Event;
import keywhiz.log.EventTag;
import keywhiz.service.crypto.RowHmacGenerator;
import keywhiz.service.permissions.PermissionCheck;
import keywhiz.test.TestDSLContexts;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BackfillRowHmacResourceTest {
  @Mock
  private RowHmacGenerator rowHmacGenerator;
  @Mock
  private AuditLog auditLog;
  @Mock
  private PermissionCheck permissionCheck;

  @Captor
  private ArgumentCaptor<Event> eventCaptor;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    doNothing().when(permissionCheck).checkAllowedOrThrow(any(), any(), any());
  }

  @Test
  public void backfillSecretHmacByNameHandlesNullOldHmac() {
    SecretsRecord record = new SecretsRecord();
    record.setId(1L);
    record.setName("foo");
    record.setRowHmac(null);

    DSLContext context = TestDSLContexts.returning(record);

    when(rowHmacGenerator.computeRowHmac(any(), any())).thenReturn("newHmac");

    BackfillRowHmacResource resource = new BackfillRowHmacResource(context, rowHmacGenerator, auditLog, permissionCheck);

    String clientName = UUID.randomUUID().toString();
    String secretName = UUID.randomUUID().toString();

    AutomationClient automationClient = newAutomationClient(clientName);

    resource.backfillSecretRowHmacByName(automationClient, secretName, true);
  }

  @Test
  public void backfillSecretHmacByNameWritesToAuditLog() {
    String oldHmac = UUID.randomUUID().toString();
    String newHmac = UUID.randomUUID().toString();

    when(rowHmacGenerator.computeRowHmac(any(), any())).thenReturn(newHmac);

    SecretsRecord record = new SecretsRecord();
    record.setId(1L);
    record.setName("foo");
    record.setRowHmac(oldHmac);

    DSLContext context = TestDSLContexts.returning(record);

    BackfillRowHmacResource resource = new BackfillRowHmacResource(context, rowHmacGenerator, auditLog, permissionCheck);

    String clientName = UUID.randomUUID().toString();
    String secretName = UUID.randomUUID().toString();

    AutomationClient automationClient = newAutomationClient(clientName);

    resource.backfillSecretRowHmacByName(automationClient, secretName, true);

    verify(auditLog).recordEvent(eventCaptor.capture());

    Event event = eventCaptor.getValue();
    assertEquals(EventTag.SECRET_BACKFILLHMAC, event.getType());
    assertEquals(clientName, event.getUser());
    assertEquals(secretName, event.getObjectName());

    Map<String, String> extraInfo = event.getExtraInfo();
    assertEquals(oldHmac, extraInfo.get("oldHmac"));
    assertEquals(newHmac, extraInfo.get("newHmac"));
  }

  private static AutomationClient newAutomationClient(String clientName) {
    ApiDate now = ApiDate.now();
    Client client = new Client(1, clientName, "1st client", null, now, "test", now, "test", null, null, true, true);
    AutomationClient automationClient = AutomationClient.of(client);
    return automationClient;
  }
}
