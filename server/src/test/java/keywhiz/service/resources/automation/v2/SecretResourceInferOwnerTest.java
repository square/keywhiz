package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.automation.v2.CreateGroupRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.automation.v2.SecretDetailResponseV2;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretResourceInferOwnerTest {
  private static final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  private static final Base64.Encoder encoder = Base64.getEncoder();
  private static final Integer RESPONSE_CODE_CREATED = 201;

  private static final String AUTOMATION_CLIENT_NAME = "client";
  private static final String AUTOMATION_CLIENT_GROUP_NAME = "Security";
  private static final String SECRET_CONTENT = "content";
  private static final String SECRET_NAME_PREFIX = "inferOwnerTest-";
  private static final String SECRET_OWNER = "secret owner";

  private SecretResourceTestHelper secretResourceTestHelper;
  private ClientResourceTestHelper clientResourceTestHelper;
  private GroupResourceTestHelper groupResourceTestHelper;

  OkHttpClient mutualSslClient;

  @Rule public final RuleChain chain = IntegrationTestRule.rule("keywhiz-test-newSecretOwnershipStrategyInfer.yaml");

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
    secretResourceTestHelper = new SecretResourceTestHelper(mutualSslClient, mapper);
    clientResourceTestHelper = new ClientResourceTestHelper(mutualSslClient, mapper);
    groupResourceTestHelper = new GroupResourceTestHelper(mutualSslClient, mapper);
  }

  @Test
  public void testCreateSecretWithNullOwnerWhenClientBelongsToOneGroup() throws IOException {
    String secretName = SECRET_NAME_PREFIX + randomString();
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name(secretName)
        .content(encode(SECRET_CONTENT))
        .build();

    Response response = secretResourceTestHelper.create(request);
    assertThat(response.code()).isEqualTo(RESPONSE_CODE_CREATED);

    SecretDetailResponseV2 details = secretResourceTestHelper.lookup(secretName
    );
    assertThat(details.name()).isEqualTo(secretName);
    assertThat(details.owner()).isEqualTo(AUTOMATION_CLIENT_GROUP_NAME);
  }

  @Test
  public void testCreateSecretWithNullOwnerWhenClientBelongsToMultipleGroups() throws IOException {
    String secretName = SECRET_NAME_PREFIX + randomString();
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name(secretName)
        .content(encode(SECRET_CONTENT))
        .build();

    String additionalGroupname = "additional group";
    createGroup(additionalGroupname);

    clientResourceTestHelper.modifyGroups(AUTOMATION_CLIENT_NAME, ModifyGroupsRequestV2.builder().addGroups(additionalGroupname).build());

    Response response = secretResourceTestHelper.create(request);
    assertThat(response.code()).isEqualTo(RESPONSE_CODE_CREATED);

    SecretDetailResponseV2 details = secretResourceTestHelper.lookup(secretName);
    assertThat(details.name()).isEqualTo(secretName);
    assertThat(details.owner()).isNull();
  }

  @Test
  public void testCreateSecretWithNullOwnerWhenClientBelongsToZeroGroup() throws IOException {
    String secretName = SECRET_NAME_PREFIX + randomString();
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name(secretName)
        .content(encode(SECRET_CONTENT))
        .build();

    clientResourceTestHelper.modifyGroups(AUTOMATION_CLIENT_NAME, ModifyGroupsRequestV2.builder().removeGroups(AUTOMATION_CLIENT_GROUP_NAME).build());

    Response response = secretResourceTestHelper.create(request);
    assertThat(response.code()).isEqualTo(RESPONSE_CODE_CREATED);

    SecretDetailResponseV2 details = secretResourceTestHelper.lookup(secretName);
    assertThat(details.name()).isEqualTo(secretName);
    assertThat(details.owner()).isNull();
  }

  @Test
  public void testCreateSecretWithOwnerName() throws IOException {
    String secretName = SECRET_NAME_PREFIX + randomString();

    assertThat(createGroup(SECRET_OWNER).code()).isEqualTo(RESPONSE_CODE_CREATED);

    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name(secretName)
        .owner(SECRET_OWNER)
        .content(encode(SECRET_CONTENT))
        .build();

    Response response = secretResourceTestHelper.create(request);
    assertThat(response.code()).isEqualTo(RESPONSE_CODE_CREATED);

    SecretDetailResponseV2 details = secretResourceTestHelper.lookup(secretName
    );
    assertThat(details.name()).isEqualTo(secretName);
    assertThat(details.owner()).isEqualTo(SECRET_OWNER);
  }

  private String randomString() {
    return UUID.randomUUID().toString();
  }

  private static String encode(String s) {
    return encoder.encodeToString(s.getBytes(UTF_8));
  }

  private Response createGroup(String name) throws IOException {
    return groupResourceTestHelper.create(CreateGroupRequestV2.builder().name(name).build());
  }
}
