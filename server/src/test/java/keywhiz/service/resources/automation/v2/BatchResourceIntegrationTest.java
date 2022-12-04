package keywhiz.service.resources.automation.v2;

import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.automation.v2.BatchCreateOrUpdateSecretRequestV2;
import keywhiz.api.automation.v2.BatchMode;
import keywhiz.api.automation.v2.CreateOrUpdateSecretInfoV2;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.service.daos.SecretDAO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

@RunWith(KeywhizTestRunner.class)
public class BatchResourceIntegrationTest {
  private static final boolean VALID_YES = true;
  private static final boolean VALID_NO = false;

  private static final boolean EXCEPTION_YES = true;
  private static final boolean EXCEPTION_NO = false;

  private static final boolean CREATED_YES = true;
  private static final boolean CREATED_NO = false;

  @Inject private BatchResource resource;
  @Inject private SecretDAO.SecretDAOFactory secretDAOFactory;

  private AutomationClient automationClient;
  private SecretDAO secretDAO;

  @Before
  public void before() {
    secretDAO = secretDAOFactory.readwrite();

    ApiDate now = ApiDate.now();
    Client client = new Client(1, "client", "1st client", null, now, "test", now, "test", null, null, true, true);
    automationClient = AutomationClient.of(client);
  }

  @Test
  public void createOrUpdateSecretsAllOrNone() {
    test(BatchMode.ALL_OR_NONE, VALID_NO,  VALID_NO,  EXCEPTION_YES, CREATED_NO,  CREATED_NO);
    test(BatchMode.ALL_OR_NONE, VALID_YES, VALID_NO,  EXCEPTION_YES, CREATED_NO,  CREATED_NO);
    test(BatchMode.ALL_OR_NONE, VALID_NO,  VALID_YES, EXCEPTION_YES, CREATED_NO,  CREATED_NO);
    test(BatchMode.ALL_OR_NONE, VALID_YES, VALID_YES, EXCEPTION_NO,  CREATED_YES, CREATED_YES);
  }

  @Test
  public void createOrUpdateSecretsFailFast() {
    test(BatchMode.FAIL_FAST, VALID_NO,  VALID_NO,  EXCEPTION_YES, CREATED_NO,  CREATED_NO);
    test(BatchMode.FAIL_FAST, VALID_YES, VALID_NO,  EXCEPTION_YES, CREATED_YES, CREATED_NO);
    test(BatchMode.FAIL_FAST, VALID_NO,  VALID_YES, EXCEPTION_YES, CREATED_NO,  CREATED_NO);
    test(BatchMode.FAIL_FAST, VALID_YES, VALID_YES, EXCEPTION_NO,  CREATED_YES, CREATED_YES);
  }

  @Test
  public void createOrUpdateSecretsBestEffort() {
    test(BatchMode.BEST_EFFORT, VALID_NO,  VALID_NO,  EXCEPTION_NO, CREATED_NO,  CREATED_NO);
    test(BatchMode.BEST_EFFORT, VALID_YES, VALID_NO,  EXCEPTION_NO, CREATED_YES, CREATED_NO);
    test(BatchMode.BEST_EFFORT, VALID_NO,  VALID_YES, EXCEPTION_NO, CREATED_NO,  CREATED_YES);
    test(BatchMode.BEST_EFFORT, VALID_YES, VALID_YES, EXCEPTION_NO, CREATED_YES, CREATED_YES);
  }

  private void test(
      String batchMode,
      boolean secret1Valid,
      boolean secret2Valid,
      boolean expectException,
      boolean expectSecret1Created,
      boolean expectSecret2Created) {

    CreateOrUpdateSecretInfoV2 secret1 = secret1Valid ? validNewSecret() : invalidNewSecret();
    CreateOrUpdateSecretInfoV2 secret2 = secret2Valid ? validNewSecret() : invalidNewSecret();

    BatchCreateOrUpdateSecretRequestV2 request = BatchCreateOrUpdateSecretRequestV2.builder()
        .batchMode(batchMode)
        .secrets(secret1, secret2)
        .build();

    if (expectException) {
      assertThrows(IllegalArgumentException.class,
          () -> resource.batchCreateOrUpdateSecrets(automationClient, request));
    } else {
      resource.batchCreateOrUpdateSecrets(automationClient, request);
    }

    assertThat(secretDAO.getSecretByName(secret1.name()).isPresent()).isEqualTo(expectSecret1Created);
    assertThat(secretDAO.getSecretByName(secret2.name()).isPresent()).isEqualTo(expectSecret2Created);
  }

  private static CreateOrUpdateSecretInfoV2 validNewSecret() {
    return CreateOrUpdateSecretInfoV2.builder()
        .content(content())
        .name(UUID.randomUUID().toString())
        .build();
  }

  private static CreateOrUpdateSecretInfoV2 invalidNewSecret() {
    return CreateOrUpdateSecretInfoV2.builder()
        .content(content())
        .name(UUID.randomUUID().toString())
        .owner(UUID.randomUUID().toString())
        .build();
  }

  private static String content() {
    return BaseEncoding.base64().encode("content".getBytes(StandardCharsets.UTF_8));
  }
}
