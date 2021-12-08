package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static java.nio.charset.StandardCharsets.UTF_8;
import static keywhiz.TestClients.clientRequest;
import static keywhiz.client.KeywhizClient.JSON;
import static org.junit.Assert.assertEquals;

public class BackfillRowHmacResourceClientIntegrationTest {
  private static final ObjectMapper mapper =
      KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  private static final Base64.Encoder encoder = Base64.getEncoder();

  OkHttpClient mutualSslClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  @Test public void backfillSecretHmacRequiresAuthentication() throws Exception {
    String secretName = UUID.randomUUID().toString();
    assertEquals(401, backfillSecretHmac(TestClients.unauthenticatedClient(), secretName, null).code());
  }

  // The HMAC is internal (never exposed to the client) so we can only really check
  // whether the call succeeds or not.
  @Test public void backfillsSecretHmacWithForceMissing() throws Exception {
    String secretName = UUID.randomUUID().toString();
    createSecret(secretName);
    assertEquals(204, backfillSecretHmac(secretName).code());
  }

  // The HMAC is internal (never exposed to the client) so we can only really check
  // whether the call succeeds or not.
  @Test public void backfillsSecretHmacWithForceTrue() throws Exception {
    String secretName = UUID.randomUUID().toString();
    createSecret(secretName);
    assertEquals(204, backfillSecretHmac(secretName, true).code());
  }

  // The HMAC is internal (never exposed to the client) so we can only really check
  // whether the call succeeds or not.
  @Test public void backfillsSecretHmacWithForceFalse() throws Exception {
    String secretName = UUID.randomUUID().toString();
    createSecret(secretName);
    assertEquals(204, backfillSecretHmac(secretName, false).code());
  }

  @Test public void backfillingNonExistentSecretThrows404() throws Exception {
    String secretName = UUID.randomUUID().toString();
    assertEquals(404, backfillSecretHmac(secretName).code());
  }

  private Response backfillSecretHmac(String secretName, boolean force) throws IOException {
    return backfillSecretHmac(mutualSslClient, secretName, force);
  }

  private Response backfillSecretHmac(String secretName) throws IOException {
    return backfillSecretHmac(mutualSslClient, secretName, null);
  }

  private Response backfillSecretHmac(OkHttpClient client, String secretName, Boolean force) throws IOException {
    String url = String.format("/automation/v2/backfill-row-hmac/secret/name/%s", secretName);
    if (force != null) {
      url += String.format("?force=%s", force);
    }
    Request request = clientRequest(url).post(emptyBody()).build();
    Response response = client.newCall(request).execute();
    return response;
  }

  private void createSecret(String secretName) throws IOException {
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name(secretName)
        .content(encode("foo"))
        .build();
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest("/automation/v2/secrets").post(body).build();
    mutualSslClient.newCall(post).execute();
  }

  private static String encode(String s) {
    return encoder.encodeToString(s.getBytes(UTF_8));
  }

  private static RequestBody emptyBody() {
    return RequestBody.create(JSON, "");
  }
}
