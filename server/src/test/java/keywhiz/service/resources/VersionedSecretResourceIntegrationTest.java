package keywhiz.service.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import javax.ws.rs.core.MediaType;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.api.CreateSecretRequest;
import keywhiz.client.KeywhizClient;
import keywhiz.model.OffsetDateTimeConverter;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.mockito.junit.MockitoJUnitRule;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionedSecretResourceIntegrationTest {
  @Rule public TestRule mockito = new MockitoJUnitRule(this);
  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  OkHttpClient mutualSslClient;
  ObjectMapper mapper = new ObjectMapper();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  @Test public void listsSecretNames() throws IOException {
    Request get = new Request.Builder()
        .get()
        .url("/v2/secrets")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(get).execute();
    List<String> secretNames = mapper.readValue(response.body().bytes(), new TypeReference<List<String>>() {});
    assertThat(secretNames).containsOnly("Nobody_PgPass", "Hacking_Password", "General_Password",
        "Database_Password", "NonexistentOwner_Pass", "Versioned_Password");
  }

  @Test public void listSecretVersions() throws IOException {
    Request get = new Request.Builder()
        .get()
        .url("/v2/secrets/Versioned_Password")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(get).execute();
    List<String> versionNames = mapper.readValue(response.body().bytes(), new TypeReference<List<String>>() {});
    assertThat(versionNames).containsOnly("0aae825a73e161d8", "0aae825a73e161e8",
        "0aae825a73e161f8", "0aae825a73e161g8");
  }

  @Test public void getsSpecificSecretVersions() throws IOException {
    Request get = new Request.Builder()
        .get()
        .url("/v2/secrets/Versioned_Password/0aae825a73e161d8")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).contains("{\"mode\":\"0400\",\"owner\":\"admin\"}");

    get = new Request.Builder()
        .get()
        .url("/v2/secrets/Versioned_Password/0aae825a73e161e8")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).contains("{\"mode\":\"0400\",\"owner\":\"new-admin\"}");
  }

  @Test public void getLatestSecretVersion() throws IOException {
    Request get = new Request.Builder()
        .get()
        .url("/v2/secrets/Versioned_Password/LATEST")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).contains(
        new OffsetDateTimeConverter().from(Timestamp.valueOf("2011-09-29 18:46:00.232")).toString());
  }

  @Test public void createsSecret() throws IOException {
    CreateSecretRequest
        request = new CreateSecretRequest("new-secret", "desc", "c3VwZXJTZWNyZXQK",
        true, ImmutableMap.of());
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url("/v2/secrets")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(201);

    Request get = new Request.Builder()
        .get()
        .url("/v2/secrets")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    response = mutualSslClient.newCall(get).execute();
    List<String> secretNames = mapper.readValue(response.body().bytes(), new TypeReference<List<String>>() {});
    assertThat(secretNames).contains("new-secret");
  }

  @Test public void createsSecretVersions() throws IOException {
    CreateSecretRequest
        request = new CreateSecretRequest("versions", "v1", "c3VwZXJTZWNyZXQK",
        true, ImmutableMap.of());
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url("/v2/secrets")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(201);

    // Request a new version
    request = new CreateSecretRequest("versions", "v2", "c3VwZXJTZWNyZXQK",
        true, ImmutableMap.of());
    body = mapper.writeValueAsString(request);
    post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url("/v2/secrets")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(201);

    // Verify only one secret series exists
    Request get = new Request.Builder()
        .get()
        .url("/v2/secrets")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    response = mutualSslClient.newCall(get).execute();
    List<String> secretNames = mapper.readValue(response.body().bytes(), new TypeReference<List<String>>() {});
    assertThat(secretNames).containsOnlyOnce("versions");

    // Verify that two versions exist
    get = new Request.Builder()
        .get()
        .url("/v2/secrets/versions")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    response = mutualSslClient.newCall(get).execute();
    List<String> secretVerisons = mapper.readValue(response.body().bytes(), new TypeReference<List<String>>() {});
    assertThat(secretVerisons.size()).isEqualTo(2);
  }

  @Test public void deletesSecretSeries() throws IOException {
    Request delete = new Request.Builder()
        .delete()
        .url("/v2/secrets/Database_Password")
        .build();

    Response response = mutualSslClient.newCall(delete).execute();
    assertThat(response.code()).isEqualTo(200);

    // Verify that no versions exist
    Request get = new Request.Builder()
        .get()
        .url("/v2/secrets/Database_Password")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(404);
  }

  @Test public void deletesSecretVersion() throws IOException {
    Request delete = new Request.Builder()
        .delete()
        .url("/v2/secrets/Versioned_Password/0aae825a73e161f8")
        .build();

    Response response = mutualSslClient.newCall(delete).execute();
    assertThat(response.code()).isEqualTo(200);

    // Verify that other versions exist
    Request get = new Request.Builder()
        .get()
        .url("/v2/secrets/Versioned_Password")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
    List<String> secretVerisons = mapper.readValue(response.body().bytes(), new TypeReference<List<String>>() {});
    assertThat(secretVerisons.size()).isEqualTo(3);

    // Verify that the deleted version does not exist
    get = new Request.Builder()
        .get()
        .url("/v2/secrets/Versioned_Password/0aae825a73e161f8")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(404);
  }
}