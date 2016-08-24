package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.automation.v2.CreateGroupRequestV2;
import keywhiz.api.automation.v2.CreateOrUpdateSecretRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.automation.v2.SecretDetailResponseV2;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static java.lang.String.format;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static keywhiz.TestClients.clientRequest;
import static keywhiz.client.KeywhizClient.JSON;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretResourceTest {
  private static final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  private static final Encoder encoder = Base64.getEncoder();
  private static final Decoder decoder = Base64.getDecoder();

  OkHttpClient mutualSslClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  //---------------------------------------------------------------------------------------
  // createSecret
  //---------------------------------------------------------------------------------------

  @Test public void createSecret_successUnVersioned() throws Exception {
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name("secret1")
        .content(encoder.encodeToString("supa secret".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build();
    Response httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(201);
    URI location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/secrets/secret1");
  }

  @Test public void createSecret_duplicateUnVersioned() throws Exception {
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name("secret2")
        .content(encoder.encodeToString("supa secret2".getBytes(UTF_8)))
        .description("desc")
        .build();
    Response httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(201);
    httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(409);
  }

  //---------------------------------------------------------------------------------------
  // createOrUpdateSecret
  //---------------------------------------------------------------------------------------

  @Test public void createOrUpdateSecret() throws Exception {
    CreateOrUpdateSecretRequestV2 request = CreateOrUpdateSecretRequestV2.builder()
        .content(encoder.encodeToString("supa secret".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build();
    Response httpResponse = createOrUpdate(request, "secret3");
    assertThat(httpResponse.code()).isEqualTo(201);
    URI location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/secrets/secret3");

    httpResponse = createOrUpdate(request, "secret3");
    assertThat(httpResponse.code()).isEqualTo(201);
    location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/secrets/secret3");
  }

  //---------------------------------------------------------------------------------------

  @Ignore
  @Test public void modifySecretSeries_notFound() throws Exception {
    // TODO: need request object
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(null));
    Request post = clientRequest("/automation/v2/secrets/non-existent").post(body).build();
    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Ignore
  @Test public void modifySecretSeries_success() throws Exception {
    // secret5
    // TODO: check different metadata, name, location
  }

  @Test public void secretInfo_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/secrets/non-existent").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretInfo_success() throws Exception {
    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret6")
        .content(encoder.encodeToString("supa secret6".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build());

    SecretDetailResponseV2 response = lookup("secret6");
    assertThat(response.name()).isEqualTo("secret6");
    assertThat(response.createdBy()).isEqualTo("client");
    assertThat(response.description()).isEqualTo("desc");
    assertThat(response.type()).isEqualTo("password");

    // These values are left out for a series lookup as they pertain to a specific secret.
    assertThat(response.content()).isEmpty();
    assertThat(response.size().longValue()).isZero();
    assertThat(response.metadata()).isEmpty();
  }

  @Test public void secretGroupsListing_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/secrets/non-existent/groups").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretGroupsListing_success() throws Exception {
    createGroup("group7a");
    createGroup("group7b");

    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret7")
        .content(encoder.encodeToString("supa secret7".getBytes(UTF_8)))
        .groups("group7a", "group7b")
        .build());

    assertThat(groupsListing("secret7")).containsOnly("group7a", "group7b");
  }

  @Test public void modifySecretGroups_notFound() throws Exception {
    ModifyGroupsRequestV2 request = ModifyGroupsRequestV2.builder().build();
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request put = clientRequest("/automation/v2/secrets/non-existent/groups").put(body).build();
    Response httpResponse = mutualSslClient.newCall(put).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void modifySecretGroups_success() throws Exception {
    // Create sample secret and groups
    createGroup("group8a");
    createGroup("group8b");
    createGroup("group8c");
    create(CreateSecretRequestV2.builder()
        .name("secret8")
        .content(encoder.encodeToString("supa secret8".getBytes(UTF_8)))
        .groups("group8a", "group8b")
        .build());

    // Modify secret
    ModifyGroupsRequestV2 request = ModifyGroupsRequestV2.builder()
        .addGroups("group8c", "non-existent1")
        .removeGroups("group8a", "non-existent2")
        .build();
    List<String> groups = modifyGroups("secret8", request);
    assertThat(groups).containsOnly("group8b", "group8c");
  }

  @Test public void deleteSecretSeries_notFound() throws Exception {
    assertThat(deleteSeries("non-existent").code()).isEqualTo(404);
  }

  @Test public void deleteSecretSeries_success() throws Exception {
    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret12")
        .content(encoder.encodeToString("supa secret12".getBytes(UTF_8)))
        .build());

    // Delete works
    assertThat(deleteSeries("secret12").code()).isEqualTo(204);

    // Subsequent deletes can't find the secret series
    assertThat(deleteSeries("secret12").code()).isEqualTo(404);
  }

  @Test public void secretListing_success() throws Exception {
    // Listing without secret16
    assertThat(listing()).doesNotContain("secret16");

    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret16")
        .content(encoder.encodeToString("supa secret16".getBytes(UTF_8)))
        .build());

    // Listing with secret16
    assertThat(listing()).contains("secret16");
  }

  @Test public void secretListingExpiry_success() throws Exception {
    // Listing without secret17,18,19
    List<String> s = listing();
    assertThat(s).doesNotContain("secret17");
    assertThat(s).doesNotContain("secret18");
    assertThat(s).doesNotContain("secret19");

    // create groups
    createGroup("group15a");
    createGroup("group15b");

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // add some secrets
    create(CreateSecretRequestV2.builder()
        .name("secret17")
        .content(encoder.encodeToString("supa secret17".getBytes(UTF_8)))
        .expiry(now+86400*3)
        .groups("group15a", "group15b")
        .build());

    create(CreateSecretRequestV2.builder()
        .name("secret18")
        .content(encoder.encodeToString("supa secret18".getBytes(UTF_8)))
        .expiry(now+86400)
        .groups("group15a")
        .build());

    create(CreateSecretRequestV2.builder()
        .name("secret19")
        .content(encoder.encodeToString("supa secret19".getBytes(UTF_8)))
        .expiry(now+86400*2)
        .groups("group15b")
        .build());

    // check limiting by group and expiry
    List<String> s1 = listExpiring(now+86400*4, "group15a");
    assertThat(s1).contains("secret17");
    assertThat(s1).contains("secret18");

    List<String> s2 = listExpiring(now+86400*4, "group15b");
    assertThat(s2).contains("secret19");
    assertThat(s2).doesNotContain("secret18");

    List<String> s3 = listExpiring(now+86400*2, null);
    assertThat(s3).contains("secret18");
    assertThat(s3).doesNotContain("secret17");
  }

  @Test public void secretVersionListing_notFound() throws Exception {
    Request put = clientRequest("/automation/v2/secrets/non-existent/versions/0-0").build();
    Response httpResponse = mutualSslClient.newCall(put).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretVersionListing_success() throws Exception {
    int totalVersions = 6;
    int sleepInterval = 1000; // Delay so secrets have different creation timestamps
    List<SecretDetailResponseV2> versions;
    assertThat(listing()).doesNotContain("secret20");

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // Create secrets 1 second apart, so that the order of the versions, which
    // will be listed by creation time, is fixed
    for (int i = 0; i < totalVersions; i++) {
      createOrUpdate(CreateOrUpdateSecretRequestV2.builder()
          .content(encoder.encodeToString(format("supa secret20_v%d", i).getBytes(UTF_8)))
          .description(format("secret20, version %d", i))
          .expiry(now + 86400 * 2)
          .metadata(ImmutableMap.of("version", Integer.toString(i)))
          .build(), "secret20");
      sleep(sleepInterval);
    }

    // List all versions of this secret
    versions = listVersions("secret20", 0, 1000);

    checkSecretVersions(versions, "secret20", totalVersions, 0, 1000);

    // List the newest half of the versions of this secret
    versions = listVersions("secret20", 0, totalVersions / 2);

    checkSecretVersions(versions, "secret20", totalVersions, 0, totalVersions / 2);

    // List the oldest half of the versions of this secret
    versions = listVersions("secret20", totalVersions / 2, totalVersions);

    checkSecretVersions(versions, "secret20", totalVersions, totalVersions / 2, totalVersions);

    // List the middle half of the versions of this secret
    versions = listVersions("secret20", totalVersions / 4, totalVersions / 2);

    checkSecretVersions(versions, "secret20", totalVersions, totalVersions / 4,
        totalVersions / 2);
  }

  @Test public void secretChangeVersion_notFound() throws Exception {
    Request post =
        clientRequest("/automation/v2/secrets/non-existent/setversion/0").post(
            RequestBody.create(JSON, mapper.writeValueAsString(null)))
            .build();
    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretChangeVersion_success() throws Exception {
    int totalVersions = 6;
    String name = "secret21";
    List<SecretDetailResponseV2> versions;
    SecretDetailResponseV2 initialCurrentVersion;
    SecretDetailResponseV2 finalCurrentVersion;

    assertThat(listing()).doesNotContain(name);

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // Create secrets
    for (int i = 0; i < totalVersions; i++) {
      createOrUpdate(CreateOrUpdateSecretRequestV2.builder()
          .content(encoder.encodeToString(format("supa secret21_v%d", i).getBytes(UTF_8)))
          .description(format("%s, version %d", name, i))
          .expiry(now + 86400 * 2)
          .metadata(ImmutableMap.of("version", Integer.toString(i)))
          .build(), name);
      sleep(2000 / totalVersions);
    }

    // Get the current version (the last version created)
    initialCurrentVersion = lookup(name);
    assertThat(initialCurrentVersion.name().equals(name));
    assertThat(
        initialCurrentVersion.description().equals(format("%s, version %d", name, totalVersions)));

    // Get the earliest version of this secret
    versions = listVersions(name, totalVersions - 2, 1);
    assertThat(!versions.get(0).equals(initialCurrentVersion));

    // Reset the current version to this version
    setCurrentVersion(name, versions.get(0).version());

    // Get the current version
    finalCurrentVersion = lookup(name);
    assertThat(finalCurrentVersion.equals(versions.get(0)));
    assertThat(!finalCurrentVersion.equals(initialCurrentVersion));
  }

  @Test public void secretChangeVersion_invalidVersion() throws Exception {
    int totalVersions = 3;
    String name = "secret22";
    List<SecretDetailResponseV2> versions;
    SecretDetailResponseV2 initialCurrentVersion;
    SecretDetailResponseV2 finalCurrentVersion;

    assertThat(listing()).doesNotContain(name);

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // Create secrets
    for (int i = 0; i < totalVersions; i++) {
      createOrUpdate(CreateOrUpdateSecretRequestV2.builder()
          .content(encoder.encodeToString(format("supa secret21_v%d", i).getBytes(UTF_8)))
          .description(format("%s, version %d", name, i))
          .expiry(now + 86400 * 2)
          .metadata(ImmutableMap.of("version", Integer.toString(i)))
          .build(), name);
    }

    // Get the current version (the last version created)
    initialCurrentVersion = lookup(name);
    assertThat(initialCurrentVersion.name().equals(name));
    assertThat(
        initialCurrentVersion.description().equals(format("%s, version %d", name, totalVersions)));

    // Get an invalid version of this secret
    versions = listVersions(name, 0, totalVersions);
    Optional<Long> maxValidVersion = versions.stream().map(v -> v.version()).max(Long::compare);

    if (maxValidVersion.isPresent()) {

      // Reset the current version to this version
      Request post = clientRequest(
          format("/automation/v2/secrets/%s/setversion/%d", name, maxValidVersion.get() + 1)).post(
          RequestBody.create(JSON, mapper.writeValueAsString(null)))
          .build();
      Response httpResponse = mutualSslClient.newCall(post).execute();
      assertThat(httpResponse.code()).isEqualTo(400);

      // Get the current version, which should not have changed
      finalCurrentVersion = lookup(name);
      assertThat(finalCurrentVersion.equals(initialCurrentVersion));
    }
  }

  /**
   * Iterates over the given list of secret versions to verify that they are sorted from most
   * recent creation date to least recent, that they have the expected version numbers,
   * and that they have the correct secret name.
   *
   * @param versions a list of information on versions of secrets
   * @param name of the secret series
   * @param totalVersions the number of versions created
   * @param versionIdx the index in the overall version list of the newest version taken
   * @param numVersions the maximum number of versions taken
   */
  private void checkSecretVersions(List<SecretDetailResponseV2> versions, String name,
      int totalVersions, int versionIdx, int numVersions) {
    long creationTime = System.currentTimeMillis() / 1000L;
    int startIdx = totalVersions - versionIdx - 1;
    int expectedVersions = Math.min(numVersions, totalVersions - versionIdx);
    // Check that we retrieved as many secrets as possible
    assertThat(versions.size()).isEqualTo(expectedVersions);

    for (SecretDetailResponseV2 version : versions) {
      // Check creation ordering
      assertThat(version.createdAtSeconds() < creationTime);
      creationTime = version.createdAtSeconds();

      // Check version number
      assertThat(version.metadata()).isEqualTo(
          ImmutableMap.of("version", Integer.toString(startIdx--)));

      // Check secret name
      assertThat(version.name()).isEqualTo(name);
    }
  }

  private Response createGroup(String name) throws IOException {
    GroupResourceTest groupResourceTest = new GroupResourceTest();
    groupResourceTest.mutualSslClient = mutualSslClient;
    return groupResourceTest.create(CreateGroupRequestV2.builder().name(name).build());
  }

  Response create(CreateSecretRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest("/automation/v2/secrets").post(body).build();
    return mutualSslClient.newCall(post).execute();
  }

  Response createOrUpdate(CreateOrUpdateSecretRequestV2 request, String name) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest(format("/automation/v2/secrets/%s", name)).post(body).build();
    return mutualSslClient.newCall(post).execute();
  }

  List<String> listing() throws IOException {
    Request get = clientRequest("/automation/v2/secrets").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>(){});
  }

  List<String> listExpiring(Long time, String groupName) throws IOException {
    String requestURL = "/automation/v2/secrets/expiring/";
    if (time != null && time > 0) {
      requestURL += time.toString() + "/";
    }
    if (groupName != null && groupName.length() > 0) {
      requestURL += groupName;
    }
    Request get = clientRequest(requestURL).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>(){});
  }

  private List<SecretDetailResponseV2> listVersions(String name, int versionIdx, int numVersions)
      throws IOException {
    Request get = clientRequest(
        format("/automation/v2/secrets/%s/versions/%d-%d", name, versionIdx, numVersions)).get()
        .build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(),
        new TypeReference<List<SecretDetailResponseV2>>() {
        });
  }

  private void setCurrentVersion(String name, long versionId)
      throws IOException {
    Request post = clientRequest(
        format("/automation/v2/secrets/%s/setversion/%d", name, versionId)).post(
        RequestBody.create(JSON, mapper.writeValueAsString(null)))
        .build();
    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
  }

  SecretDetailResponseV2 lookup(String name) throws IOException {
    Request get = clientRequest("/automation/v2/secrets/" + name).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), SecretDetailResponseV2.class);
  }

  List<String> groupsListing(String name) throws IOException {
    Request get = clientRequest(format("/automation/v2/secrets/%s/groups", name)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>(){});
  }

  List<String> modifyGroups(String name, ModifyGroupsRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request put = clientRequest(format("/automation/v2/secrets/%s/groups", name)).put(body).build();
    Response httpResponse = mutualSslClient.newCall(put).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>(){});
  }

  Response deleteSeries(String name) throws IOException {
    Request delete = clientRequest("/automation/v2/secrets/" + name).delete().build();
    return mutualSslClient.newCall(delete).execute();
  }
}
