package keywhiz.service.resources.automation.v2;

import keywhiz.KeywhizConfig;
import keywhiz.api.ApiDate;
import keywhiz.api.automation.v2.BatchCreateOrUpdateSecretsRequestV2;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.log.AuditLog;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.permissions.PermissionCheck;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;

import static org.junit.Assert.assertThrows;

public class BatchResourceTest {
  @Rule public MethodRule mockito = MockitoJUnit.rule();

  @Mock DSLContext jooq;
  @Mock SecretController secretController;
  @Mock AclDAO.AclDAOFactory aclDAOFactory;
  @Mock SecretDAO.SecretDAOFactory secretDAOFactory;
  @Mock AuditLog auditLog;
  @Mock PermissionCheck permissionCheck;
  @Mock KeywhizConfig keywhizConfig;

  private AutomationClient automationClient;

  private BatchResource resource;

  @Before
  public void before() {
    ApiDate now = ApiDate.now();
    Client client = new Client(1, "client", "1st client", null, now, "test", now, "test", null, null, true, true);
    automationClient = AutomationClient.of(client);

    resource = new BatchResource(
        jooq,
        secretController,
        aclDAOFactory,
        secretDAOFactory,
        auditLog,
        permissionCheck,
        keywhizConfig);
  }

  @Test
  public void rejectsUnknownBatchMode() {
    BatchCreateOrUpdateSecretsRequestV2 request = BatchCreateOrUpdateSecretsRequestV2.builder()
        .batchMode("foo")
        .secrets()
        .build();

    assertThrows(
        IllegalArgumentException.class,
        () -> resource.batchCreateOrUpdateSecrets(automationClient, request));
  }
}
