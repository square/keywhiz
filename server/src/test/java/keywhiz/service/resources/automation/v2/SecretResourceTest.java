package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.automation.v2.CreateGroupRequestV2;
import keywhiz.api.automation.v2.CreateOrUpdateSecretRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.automation.v2.PartialUpdateSecretRequestV2;
import keywhiz.api.automation.v2.SecretContentsRequestV2;
import keywhiz.api.automation.v2.SecretContentsResponseV2;
import keywhiz.api.automation.v2.SecretDetailResponseV2;
import keywhiz.api.automation.v2.SetSecretVersionRequestV2;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SanitizedSecretWithGroups;
import keywhiz.api.model.SanitizedSecretWithGroupsListAndCursor;
import keywhiz.api.model.SecretRetrievalCursor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static java.lang.String.format;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static keywhiz.TestClients.clientRequest;
import static keywhiz.client.KeywhizClient.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SecretResourceTest {
  private static final ObjectMapper mapper =
      KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  private static final Encoder encoder = Base64.getEncoder();

  SecretResourceTestHelper secretResourceTestHelper;

  OkHttpClient mutualSslClient;

  @ClassRule public static RuleChain chain = IntegrationTestRule.rule(
      "keywhiz-test.yaml");

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
    secretResourceTestHelper = new SecretResourceTestHelper(mutualSslClient, mapper);
  }

  //---------------------------------------------------------------------------------------
  // renameSecret
  //---------------------------------------------------------------------------------------

  @Test public void renamesSecret() throws Exception {
    String oldName = UUID.randomUUID().toString();
    createSecret(oldName);

    String newName = UUID.randomUUID().toString();
    Response response = renameSecret(oldName, newName);
    assertEquals(201, response.code());
    assertEquals(
        "https://localhost:4445/automation/v2/secrets/" + newName,
        response.header("Location"));

    assertFalse(secretResourceTestHelper.getSecret(oldName).isPresent());
    assertTrue(secretResourceTestHelper.getSecret(newName).isPresent());
  }

  @Test public void cannotRenameSecretToExistingSecret() throws Exception {
    String oldName = UUID.randomUUID().toString();
    createSecret(oldName);

    String newName = UUID.randomUUID().toString();
    createSecret(newName);

    assertEquals(409, renameSecret(oldName, newName).code());
  }

  @Test public void cannotRenameNonexistentSecret() throws Exception {
    String oldName = UUID.randomUUID().toString();
    String newName = UUID.randomUUID().toString();
    assertEquals(404, renameSecret(oldName, newName).code());
  }

  //---------------------------------------------------------------------------------------
  // createSecret
  //---------------------------------------------------------------------------------------

  @Test public void createSecretWithOwner() throws Exception {
    String secretName = UUID.randomUUID().toString();
    String ownerName = UUID.randomUUID().toString();

    assertEquals(201, createGroup(ownerName).code());

    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name(secretName)
        .owner(ownerName)
        .content(encode("foo"))
        .build();
   secretResourceTestHelper.create(request);

    SecretDetailResponseV2 details = secretResourceTestHelper.lookup(secretName);
    assertEquals(ownerName, details.owner());
  }

  @Test public void createSecret_successUnVersioned() throws Exception {
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name("secret1")
        .content(encoder.encodeToString("supa secret".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build();
    Response httpResponse =secretResourceTestHelper.create(request);
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
    Response httpResponse =secretResourceTestHelper.create(request);
    assertThat(httpResponse.code()).isEqualTo(201);
    httpResponse =secretResourceTestHelper.create(request);
    assertThat(httpResponse.code()).isEqualTo(409);
  }

  //---------------------------------------------------------------------------------------
  // createOrUpdateSecret
  //---------------------------------------------------------------------------------------

  @Test public void createOrUpdateSecretPreservesExistingNullSecretOwner() throws Exception {
    String secretName = UUID.randomUUID().toString();

    CreateSecretRequestV2 createRequest = CreateSecretRequestV2.builder()
        .name(secretName)
        .content(encode("foo"))
        .build();
   secretResourceTestHelper.create(createRequest);

    SecretDetailResponseV2 originalDetails = secretResourceTestHelper.lookup(secretName);
    assertNull(originalDetails.owner());

    CreateOrUpdateSecretRequestV2 updateRequest = CreateOrUpdateSecretRequestV2.builder()
        .content(encode("bar"))
        .description(UUID.randomUUID().toString())
        .build();

    assertEquals(201, secretResourceTestHelper.createOrUpdate(updateRequest, secretName).code());

    SecretDetailResponseV2 updatedDetails = secretResourceTestHelper.lookup(secretName);
    assertNull(updatedDetails.owner());
  }

  @Test public void createOrUpdateSecretPreservesExistingNonNullSecretOwner() throws Exception {
    String secretName = UUID.randomUUID().toString();
    String ownerName = UUID.randomUUID().toString();

    assertEquals(201, createGroup(ownerName).code());

    CreateSecretRequestV2 createRequest = CreateSecretRequestV2.builder()
        .name(secretName)
        .owner(ownerName)
        .content(encode("foo"))
        .build();
   secretResourceTestHelper.create(createRequest);

    SecretDetailResponseV2 originalDetails = secretResourceTestHelper.lookup(secretName);
    assertEquals(ownerName, originalDetails.owner());

    CreateOrUpdateSecretRequestV2 updateRequest = CreateOrUpdateSecretRequestV2.builder()
        .content(encode("bar"))
        .description(UUID.randomUUID().toString())
        .build();

    assertEquals(201, secretResourceTestHelper.createOrUpdate(updateRequest, secretName).code());

    SecretDetailResponseV2 updatedDetails = secretResourceTestHelper.lookup(secretName);
    assertNotNull("Updated secret's owner was null", updatedDetails.owner());
    assertEquals(ownerName, updatedDetails.owner());
  }

  @Test public void createOrUpdateSecret() throws Exception {
    CreateOrUpdateSecretRequestV2 request = CreateOrUpdateSecretRequestV2.builder()
        .content(encoder.encodeToString("supa secret".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build();
    Response httpResponse = secretResourceTestHelper.createOrUpdate(request, "secret3");
    assertThat(httpResponse.code()).isEqualTo(201);
    URI location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/secrets/secret3");

    httpResponse = secretResourceTestHelper.createOrUpdate(request, "secret3");
    assertThat(httpResponse.code()).isEqualTo(201);
    location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/secrets/secret3");
  }

  @Test public void createOrUpdateSecretUpdateOwner() throws Exception {
    String secretName = UUID.randomUUID().toString();
    String ownerName = UUID.randomUUID().toString();

    assertEquals(201, createGroup(ownerName).code());

    CreateSecretRequestV2 createRequest = CreateSecretRequestV2.builder()
        .name(secretName)
        .owner(ownerName)
        .content(encode("foo"))
        .build();
    secretResourceTestHelper.create(createRequest);

    SecretDetailResponseV2 originalDetails = secretResourceTestHelper.lookup(secretName);
    assertEquals(ownerName, originalDetails.owner());

    String updatedOwnerName = UUID.randomUUID().toString();
    assertEquals(201, createGroup(updatedOwnerName).code());

    CreateOrUpdateSecretRequestV2 updateRequest = CreateOrUpdateSecretRequestV2.builder()
        .content(encode("bar"))
        .description(UUID.randomUUID().toString())
        .owner(updatedOwnerName)
        .build();

    assertEquals(201, secretResourceTestHelper.createOrUpdate(updateRequest, secretName).code());

    SecretDetailResponseV2 updatedDetails = secretResourceTestHelper.lookup(secretName);
    assertNotNull("Updated secret's owner was null", updatedDetails.owner());
    assertEquals(updatedOwnerName, updatedDetails.owner());
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

  //---------------------------------------------------------------------------------------
  // partialUpdateSecret
  //---------------------------------------------------------------------------------------
  @Test
  public void partialUpdateSecret_success() throws Exception {
    // Create a secret to update
    CreateOrUpdateSecretRequestV2 createRequest = CreateOrUpdateSecretRequestV2.builder()
        .content(encoder.encodeToString("supa secret".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build();
    Response httpResponse = secretResourceTestHelper.createOrUpdate(createRequest, "secret3");
    assertThat(httpResponse.code()).isEqualTo(201);
    URI location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/secrets/secret3");

    // Update the secret's description and set its expiry
    PartialUpdateSecretRequestV2 request = PartialUpdateSecretRequestV2.builder()
        .description("a more detailed description")
        .descriptionPresent(true)
        .expiry(1487268151L)
        .expiryPresent(true)
        .build();
    httpResponse = secretResourceTestHelper.partialUpdate(request, "secret3");
    assertThat(httpResponse.code()).isEqualTo(201);
    location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/secrets/secret3");

    // Check the characteristics of the updated secret
    SecretDetailResponseV2 response = secretResourceTestHelper.lookup("secret3");
    assertThat(response.name()).isEqualTo("secret3");
    assertThat(response.createdBy()).isEqualTo("client");
    assertThat(response.updatedBy()).isEqualTo("client");
    assertThat(response.contentCreatedBy()).isEqualTo("client");
    assertThat(response.updatedAtSeconds()).isEqualTo(response.contentCreatedAtSeconds());
    assertThat(response.type()).isEqualTo("password");
    assertThat(response.metadata()).isEqualTo(ImmutableMap.of("owner", "root", "mode", "0440"));
    assertThat(response.description()).isEqualTo("a more detailed description");
    assertThat(response.expiry()).isEqualTo(1487268151L);
  }

  @Test
  public void partialUpdateSecret_notFound() throws Exception {
    PartialUpdateSecretRequestV2 request = PartialUpdateSecretRequestV2.builder()
        .description("a more detailed description")
        .descriptionPresent(true)
        .expiry(1487268151L)
        .expiryPresent(true)
        .build();
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest("/automation/v2/secrets/non-existent/partialupdate").post(body).build();
    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  //---------------------------------------------------------------------------------------
  // secretInfo
  //---------------------------------------------------------------------------------------

  @Test public void secretInfo_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/secrets/non-existent").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretInfo_success() throws Exception {
    // Sample secret
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret6")
        .content(encoder.encodeToString("supa secret6".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build());

    SecretDetailResponseV2 response = secretResourceTestHelper.lookup("secret6");
    assertThat(response.name()).isEqualTo("secret6");
    assertThat(response.createdBy()).isEqualTo("client");
    assertThat(response.updatedBy()).isEqualTo("client");
    assertThat(response.createdAtSeconds()).isEqualTo(response.updatedAtSeconds());
    assertThat(response.contentCreatedBy()).isEqualTo("client");
    assertThat(response.createdAtSeconds()).isEqualTo(response.contentCreatedAtSeconds());
    assertThat(response.description()).isEqualTo("desc");
    assertThat(response.type()).isEqualTo("password");
    assertThat(response.metadata()).isEqualTo(ImmutableMap.of("owner", "root", "mode", "0440"));
  }

  //---------------------------------------------------------------------------------------
  // getSanitizedSecret
  //---------------------------------------------------------------------------------------

  @Test public void getSanitizedSecret_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/secrets/non-existent/sanitized").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void getSanitizedSecret_success() throws Exception {
    // Sample secret
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret12455")
        .content(encoder.encodeToString("supa secret12455".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build());

    SanitizedSecret response = secretResourceTestHelper.lookupSanitizedSecret("secret12455");
    assertThat(response.name()).isEqualTo("secret12455");
    assertThat(response.createdBy()).isEqualTo("client");
    assertThat(response.updatedBy()).isEqualTo("client");
    assertThat(response.contentCreatedBy()).isEqualTo("client");
    assertThat(response.description()).isEqualTo("desc");
    assertThat(response.type()).isEqualTo(Optional.of("password"));
    assertThat(response.metadata()).isEqualTo(ImmutableMap.of("owner", "root", "mode", "0440"));
  }

  //---------------------------------------------------------------------------------------
  // secretContents
  //---------------------------------------------------------------------------------------

  @Test public void secretContents_empty() throws Exception {
    // No error expected when the list of requested secrets is empty
    SecretContentsResponseV2 resp = secretResourceTestHelper.contents(SecretContentsRequestV2.fromParts(ImmutableSet.of())
    );
    assertThat(resp.successSecrets().isEmpty()).isTrue();
    assertThat(resp.missingSecrets().isEmpty()).isTrue();
  }

  @Test public void secretContents_success() throws Exception {
    // Sample secrets
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret23a")
        .content(encoder.encodeToString("supa secret23a".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret23b")
        .content(encoder.encodeToString("supa secret23b".getBytes(UTF_8)))
        .description("desc")
        .build());

    SecretContentsRequestV2 request = SecretContentsRequestV2.fromParts(
        ImmutableSet.of("secret23a", "secret23b", "non-existent")
    );
    SecretContentsResponseV2 response = secretResourceTestHelper.contents(request);
    assertThat(response.successSecrets()).isEqualTo(ImmutableMap.of("secret23a",
        encoder.encodeToString("supa secret23a".getBytes(UTF_8)),
        "secret23b", encoder.encodeToString("supa secret23b".getBytes(UTF_8))));
    assertThat(response.missingSecrets()).isEqualTo(ImmutableList.of("non-existent"));
  }


  //---------------------------------------------------------------------------------------
  // secretGroupsListing
  //---------------------------------------------------------------------------------------

  @Test public void secretGroupsListing_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/secrets/non-existent/groups").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretGroupsListing_success() throws Exception {
    createGroup("group7a");
    createGroup("group7b");

    // Sample secret
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret7")
        .content(encoder.encodeToString("supa secret7".getBytes(UTF_8)))
        .groups("group7a", "group7b")
        .build());

    assertThat(secretResourceTestHelper.groupsListing("secret7")).containsOnly("group7a", "group7b");
  }

  //---------------------------------------------------------------------------------------
  // modifySecretGroups
  //---------------------------------------------------------------------------------------

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
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret8")
        .content(encoder.encodeToString("supa secret8".getBytes(UTF_8)))
        .groups("group8a", "group8b")
        .build());

    // Modify secret
    ModifyGroupsRequestV2 request = ModifyGroupsRequestV2.builder()
        .addGroups("group8c", "non-existent1")
        .removeGroups("group8a", "non-existent2")
        .build();
    List<String> groups = secretResourceTestHelper.modifyGroups("secret8", request);
    assertThat(groups).containsOnly("group8b", "group8c");
  }

  //---------------------------------------------------------------------------------------
  // deleteSecretSeries
  //---------------------------------------------------------------------------------------

  @Test public void deleteSecretSeries_notFound() throws Exception {
    assertThat(secretResourceTestHelper.deleteSeries("non-existent").code()).isEqualTo(404);
  }

  @Test public void deleteSecretSeries_success() throws Exception {
    // Sample secret
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret12")
        .content(encoder.encodeToString("supa secret12".getBytes(UTF_8)))
        .build());

    createGroup("testGroup");
    ModifyGroupsRequestV2 request = ModifyGroupsRequestV2.builder()
        .addGroups("testGroup", "secret12")
        .build();
    secretResourceTestHelper.modifyGroups("secret12", request);

    // Delete works
    assertThat(secretResourceTestHelper.deleteSeries("secret12").code()).isEqualTo(204);

    // Subsequent deletes can't find the secret series
    assertThat(secretResourceTestHelper.deleteSeries("secret12").code()).isEqualTo(404);
  }

  //---------------------------------------------------------------------------------------
  // secretListing
  //---------------------------------------------------------------------------------------

  @Test public void secretListing_success() throws Exception {
    // Listing without secret16
    assertThat(secretResourceTestHelper.listing()).doesNotContain("secret16");

    // Sample secret
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret16")
        .description("test secret 16")
        .content(encoder.encodeToString("supa secret16".getBytes(UTF_8)))
        .build());

    // Listing with secret16
    assertThat(secretResourceTestHelper.listing()).contains("secret16");

    List<SanitizedSecret> secrets = secretResourceTestHelper.listingV2();
    boolean found = false;
    for (SanitizedSecret s : secrets) {
      if (s.name().equals("secret16")) {
        found = true;
        assertThat(s.description()).isEqualTo("test secret 16");
      }
    }
    assertThat(found).isTrue();
  }

  @Test public void secretListingBatch_success() throws Exception {
    // Listing without secret23, 24, 25
    String name1 = "secret23";
    String name2 = "secret24";
    String name3 = "secret25";
    List<String> s = secretResourceTestHelper.listing();
    assertThat(s).doesNotContain(name1);
    assertThat(s).doesNotContain(name2);
    assertThat(s).doesNotContain(name3);

    // create groups
    createGroup("group16a");
    createGroup("group16b");

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // add some secrets
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name(name1)
        .content(encoder.encodeToString("supa secret17".getBytes(UTF_8)))
        .expiry(now + 86400 * 3)
        .groups("group16a", "group16b")
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name(name2)
        .content(encoder.encodeToString("supa secret18".getBytes(UTF_8)))
        .expiry(now + 86400)
        .groups("group16a")
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name(name3)
        .content(encoder.encodeToString("supa secret19".getBytes(UTF_8)))
        .expiry(now + 86400 * 2)
        .groups("group16b")
        .build());

    // check limiting by batch (hard to test because the batch results heavily depend on other
    // tests, which may be run in parallel and often execute fast enough that different tests'
    // secrets have the same creation time as the secrets created in this test)
    List<String> s1 = secretResourceTestHelper.listBatch(0, 2, false);
    assertThat(s1.size()).isEqualTo(2);

    List<SanitizedSecret> s3 = secretResourceTestHelper.listBatchV2(0, 2, true);
    assertThat(s3.size()).isEqualTo(2);
  }

  @Test public void secretListingBatch_failure() throws Exception {
    // check that negative inputs fail
    Request get = clientRequest(String.format("/automation/v2/secrets?idx=%d&num=%d&newestFirst=%s", -1, 3, false)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(400);

    get = clientRequest(String.format("/automation/v2/secrets?idx=%d&num=%d&newestFirst=%s", 0, -3, true)).get().build();
    httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(400);

    get = clientRequest(String.format("/automation/v2/secrets/v2?idx=%d&num=%d&newestFirst=%s", -1, 3, false)).get().build();
    httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(400);

    get = clientRequest(String.format("/automation/v2/secrets/v2?idx=%d&num=%d&newestFirst=%s", 0, -3, true)).get().build();
    httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(400);
  }

  //---------------------------------------------------------------------------------------
  // backfillExpiration
  //---------------------------------------------------------------------------------------

  @Test
  public void backfillExpirationTest() throws Exception {
    byte[] certs = Resources.toByteArray(Resources.getResource("fixtures/expiring-certificates.crt"));
    byte[] pubring = Resources.toByteArray(Resources.getResource("fixtures/expiring-pubring.gpg"));
    byte[] p12 = Resources.toByteArray(Resources.getResource("fixtures/expiring-keystore.p12"));
    byte[] jceks = Resources.toByteArray(Resources.getResource("fixtures/expiring-keystore.jceks"));

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("certificate-chain.crt")
        .content(encoder.encodeToString(certs))
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("public-keyring.gpg")
        .content(encoder.encodeToString(pubring))
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("keystore.p12")
        .content(encoder.encodeToString(p12))
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("keystore.jceks")
        .content(encoder.encodeToString(jceks))
        .build());

    Response response = secretResourceTestHelper.backfillExpiration("certificate-chain.crt", ImmutableList.of());
    assertThat(response.isSuccessful()).isTrue();

    response = secretResourceTestHelper.backfillExpiration("public-keyring.gpg", ImmutableList.of());
    assertThat(response.isSuccessful()).isTrue();

    response = secretResourceTestHelper.backfillExpiration("keystore.p12", ImmutableList.of("password"));
    assertThat(response.isSuccessful()).isTrue();

    response = secretResourceTestHelper.backfillExpiration("keystore.jceks", ImmutableList.of("password"));
    assertThat(response.isSuccessful()).isTrue();

    SecretDetailResponseV2 details = secretResourceTestHelper.lookup("certificate-chain.crt");
    assertThat(details.expiry()).isEqualTo(1501533950);

    details = secretResourceTestHelper.lookup("public-keyring.gpg");
    assertThat(details.expiry()).isEqualTo(1536442365);

    details = secretResourceTestHelper.lookup("keystore.p12");
    assertThat(details.expiry()).isEqualTo(1681596851);

    details = secretResourceTestHelper.lookup("keystore.jceks");
    assertThat(details.expiry()).isEqualTo(1681596851);
  }

  //---------------------------------------------------------------------------------------
  // secretListingExpiry
  //---------------------------------------------------------------------------------------

  @Test public void secretListingExpiry_success() throws Exception {
    // Listing without secret17,18,19
    List<String> initialList = secretResourceTestHelper.listing();
    assertThat(initialList).doesNotContain("secret17");
    assertThat(initialList).doesNotContain("secret18");
    assertThat(initialList).doesNotContain("secret19");

    // create groups
    createGroup("group15a");
    createGroup("group15b");

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // add some secrets
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret17")
        .content(encoder.encodeToString("supa secret17".getBytes(UTF_8)))
        .expiry(now + 86400 * 3)
        .groups("group15a", "group15b")
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret18")
        .content(encoder.encodeToString("supa secret18".getBytes(UTF_8)))
        .expiry(now + 86400)
        .groups("group15a")
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret19")
        .content(encoder.encodeToString("supa secret19".getBytes(UTF_8)))
        .expiry(now + 86400 * 2)
        .groups("group15b")
        .build());

    // check limiting by group and expiry
    List<String> s1 = secretResourceTestHelper.listExpiring(now + 86400 * 4, "group15a");
    assertThat(s1).contains("secret17");
    assertThat(s1).contains("secret18");

    List<String> s2 = secretResourceTestHelper.listExpiring(now + 86400 * 4, "group15b");
    assertThat(s2).contains("secret19");
    assertThat(s2).doesNotContain("secret18");

    List<String> s3 = secretResourceTestHelper.listExpiring(now + 86400 * 3, null);
    assertThat(s3).contains("secret18");
    assertThat(s3).doesNotContain("secret17");

    List<SanitizedSecret> s4 = secretResourceTestHelper.listExpiringV2(now + 86400 * 3, null);
    List<String> s4Names = s4.stream().map(SanitizedSecret::name).collect(toList());
    assertListContainsSecretsWithNames(s4Names, ImmutableList.of("secret18", "secret19"));
    assertListDoesNotContainSecretsWithNames(s4Names, ImmutableList.of("secret17"));

    List<SanitizedSecretWithGroups> s5 = secretResourceTestHelper.listExpiringV3(now + 86400 * 3
    );
    List<String> s5Names = s5.stream().map(s -> s.secret().name()).collect(toList());
    assertListContainsSecretsWithNames(s5Names, ImmutableList.of("secret18", "secret19"));
    assertListDoesNotContainSecretsWithNames(s5Names, ImmutableList.of("secret17"));
 }

  @Test public void secretListingExpiry_successWithCursor() throws Exception {
    // Listing without secret26,27,28
    List<String> initialSecrets = secretResourceTestHelper.listing();
    assertThat(initialSecrets).doesNotContain("secret26");
    assertThat(initialSecrets).doesNotContain("secret27");
    assertThat(initialSecrets).doesNotContain("secret28");

    // create groups
    createGroup("group15a");
    createGroup("group15b");

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // add some secrets
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret26")
        .content(encoder.encodeToString("supa secret26".getBytes(UTF_8)))
        .expiry(now + 86400 * 3)
        .groups("group15a", "group15b")
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret27")
        .content(encoder.encodeToString("supa secret27".getBytes(UTF_8)))
        .expiry(now + 86400)
        .groups("group15a")
        .build());

   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name("secret28")
        .content(encoder.encodeToString("supa secret28".getBytes(UTF_8)))
        .expiry(now + 86400 * 2)
        .groups("group15b")
        .build());

    // check limiting by expiry
    List<SanitizedSecretWithGroups> s1 = listExpiringV4HandlingCursor(null,now + 86400 * 3, null);
    List<String> s1Names = s1.stream().map(s -> s.secret().name()).collect(toList());
    assertListContainsSecretsWithNames(s1Names, ImmutableList.of("secret27", "secret28"));
    assertListDoesNotContainSecretsWithNames(s1Names, ImmutableList.of("secret26"));

    // check varying batch size limits
    List<SanitizedSecretWithGroups> s2 = listExpiringV4HandlingCursor(null, now + 86400 * 5, 5);
    List<String> s2Names = s2.stream().map(s -> s.secret().name()).collect(toList());
    assertListContainsSecretsWithNames(s2Names, ImmutableList.of("secret27", "secret28", "secret26"));

    List<SanitizedSecretWithGroups> s3 = listExpiringV4HandlingCursor(null,now + 86400 * 5, 2);
    List<String> s3Names = s3.stream().map(s -> s.secret().name()).collect(toList());
    assertListContainsSecretsWithNames(s3Names, ImmutableList.of("secret27", "secret28", "secret26"));

    List<SanitizedSecretWithGroups> s4 = listExpiringV4HandlingCursor(null,now + 86400 * 5, 1);
    List<String> s4Names = s4.stream().map(s -> s.secret().name()).collect(toList());
    assertListContainsSecretsWithNames(s4Names, ImmutableList.of("secret27", "secret28", "secret26"));

    // check max expiration limit with small batch size
    List<SanitizedSecretWithGroups> s5 = listExpiringV4HandlingCursor(null,now + 86400 * 3, 1);
    List<String> s5Names = s5.stream().map(s -> s.secret().name()).collect(toList());
    assertListContainsSecretsWithNames(s5Names, ImmutableList.of("secret27", "secret28"));
    assertListDoesNotContainSecretsWithNames(s5Names, ImmutableList.of("secret26"));

    // check limiting by min expiry
    List<SanitizedSecretWithGroups> s6 = listExpiringV4HandlingCursor(now + 90000,now + 86400 * 4, null);
    List<String> s6Names = s6.stream().map(s -> s.secret().name()).collect(toList());
    assertListContainsSecretsWithNames(s6Names, ImmutableList.of("secret28", "secret26"));
    assertListDoesNotContainSecretsWithNames(s6Names, ImmutableList.of("secret27"));
  }

  private List<SanitizedSecretWithGroups> listExpiringV4HandlingCursor(Long minTime, Long maxTime, Integer limit)
      throws Exception {
    List<SanitizedSecretWithGroups> allRetrievedSecrets = new ArrayList<>();
    SecretRetrievalCursor cursor = null;
    do {
      SanitizedSecretWithGroupsListAndCursor retrievedSecretsAndCursor =
          secretResourceTestHelper.listExpiringV4(minTime, maxTime, limit, cursor);
      cursor = retrievedSecretsAndCursor.decodedCursor();

      List<SanitizedSecretWithGroups> secrets = retrievedSecretsAndCursor.secrets();
      assertThat(secrets).isNotNull();
      if (limit != null) {
        assertThat(secrets.size()).isLessThanOrEqualTo(limit);
      }

      allRetrievedSecrets.addAll(secrets);
    } while (cursor != null);
    return allRetrievedSecrets;
  }

  private void assertListContainsSecretsWithNames(List<String> secretNames,
      List<String> expectedNames) {
    List<String> foundNames = new ArrayList<>();
    for (String name : secretNames) {
      if (expectedNames.contains(name)) {
        foundNames.add(name);
      }
    }
    assertThat(foundNames).as(
        "List should contain secrets with names %s; found names %s in secret list %s", expectedNames,
        foundNames, secretNames)
        .containsExactlyElementsOf(expectedNames);
  }

  private void assertListDoesNotContainSecretsWithNames(List<String> secretNames,
      List<String> names) {
    List<String> foundNames = new ArrayList<>();
    for (String name : secretNames) {
      if (names.contains(name)) {
        foundNames.add(name);
      }
    }
    assertThat(foundNames).as(
        "List should not contain secrets with names %s; found names %s in secret list %s", names,
        foundNames, secretNames)
        .isEmpty();
  }

  //---------------------------------------------------------------------------------------
  // secretVersions
  //---------------------------------------------------------------------------------------

  @Test public void secretVersionListing_notFound() throws Exception {
    Request put = clientRequest("/automation/v2/secrets/non-existent/versions/0-0").build();
    Response httpResponse = mutualSslClient.newCall(put).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretVersionListing_success() throws Exception {
    int totalVersions = 6;
    int sleepInterval = 1100; // Delay so secrets have different creation timestamps
    List<SecretDetailResponseV2> versions;
    assertThat(secretResourceTestHelper.listing()).doesNotContain("secret20");

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // Create secrets 1 second apart, so that the order of the versions, which
    // will be listed by content creation time, is fixed
    for (int i = 0; i < totalVersions; i++) {
      secretResourceTestHelper.createOrUpdate(CreateOrUpdateSecretRequestV2.builder()
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

  //---------------------------------------------------------------------------------------
  // resetSecretVersion
  //---------------------------------------------------------------------------------------

  @Test public void secretChangeVersion_notFound() throws Exception {
    Request post =
        clientRequest("/automation/v2/secrets/non-existent/setversion").post(
            RequestBody.create(JSON, mapper.writeValueAsString(
                SetSecretVersionRequestV2.builder().name("non-existent").version(0).build())))
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

    assertThat(secretResourceTestHelper.listing()).doesNotContain(name);

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // Create secrets
    for (int i = 0; i < totalVersions; i++) {
      secretResourceTestHelper.createOrUpdate(CreateOrUpdateSecretRequestV2.builder()
          .content(encoder.encodeToString(format("supa secret21_v%d", i).getBytes(UTF_8)))
          .description(format("%s, version %d", name, i))
          .expiry(now + 86400 * 2)
          .metadata(ImmutableMap.of("version", Integer.toString(i)))
          .build(), name);
      sleep(2000 / totalVersions);
    }

    // Get the current version (the last version created)
    initialCurrentVersion = secretResourceTestHelper.lookup(name);
    assertThat(initialCurrentVersion.name()).isEqualTo(name);
    assertThat(
        initialCurrentVersion.description()).isEqualTo(format("%s, version %d", name, totalVersions - 1));

    // Get the earliest version of this secret
    versions = listVersions(name, totalVersions - 3, 1);
    assertThat(versions.get(0)).isNotEqualTo(initialCurrentVersion);

    // Reset the current version to this version
    setCurrentVersion(
        SetSecretVersionRequestV2.builder().name(name).version(versions.get(0).version()).build());

    // Get the current version
    finalCurrentVersion = secretResourceTestHelper.lookup(name);
    assertThat(finalCurrentVersion).isEqualToIgnoringGivenFields(versions.get(0), "updatedAtSeconds");
    assertThat(finalCurrentVersion).isNotEqualTo(initialCurrentVersion);
  }

  @Test public void secretChangeVersion_invalidVersion() throws Exception {
    int totalVersions = 3;
    String name = "secret22";
    List<SecretDetailResponseV2> versions;
    SecretDetailResponseV2 initialCurrentVersion;
    SecretDetailResponseV2 finalCurrentVersion;

    assertThat(secretResourceTestHelper.listing()).doesNotContain(name);

    // get current time to calculate timestamps off for expiry
    long now = System.currentTimeMillis() / 1000L;

    // Create secrets
    for (int i = 0; i < totalVersions; i++) {
      secretResourceTestHelper.createOrUpdate(CreateOrUpdateSecretRequestV2.builder()
          .content(encoder.encodeToString(format("supa secret22_v%d", i).getBytes(UTF_8)))
          .description(format("%s, version %d", name, i))
          .expiry(now + 86400 * 2)
          .metadata(ImmutableMap.of("version", Integer.toString(i)))
          .build(), name);
    }

    // Get the current version (the last version created)
    initialCurrentVersion = secretResourceTestHelper.lookup(name);
    assertThat(initialCurrentVersion.name()).isEqualTo(name);
    assertThat(initialCurrentVersion.description()).isEqualTo(format("%s, version %d", name, totalVersions - 1));

    // Get an invalid version of this secret
    versions = listVersions(name, 0, totalVersions);
    Optional<Long> maxValidVersion = versions.stream().map(SecretDetailResponseV2::version).max(Long::compare);

    if (maxValidVersion.isPresent()) {
      // Reset the current version to this version
      Request post = clientRequest(String.format("/automation/v2/secrets/%s/setversion", name)).post(
          RequestBody.create(JSON, mapper.writeValueAsString(SetSecretVersionRequestV2.builder()
              .name(name)
              .version(maxValidVersion.get() + 1)
              .build()))).build();
      Response httpResponse = mutualSslClient.newCall(post).execute();
      assertThat(httpResponse.code()).isEqualTo(400);

      // Get the current version, which should not have changed
      finalCurrentVersion = secretResourceTestHelper.lookup(name);
      assertThat(finalCurrentVersion).isEqualTo(initialCurrentVersion);
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
      assertThat(version.contentCreatedAtSeconds()).isLessThan(creationTime);
      creationTime = version.contentCreatedAtSeconds();

      // Check version number
      assertThat(version.metadata()).isEqualTo(
          ImmutableMap.of("version", Integer.toString(startIdx--)));

      // Check secret name
      assertThat(version.name()).isEqualTo(name);
    }
  }


  //---------------------------------------------------------------------------------------
  // Version handling after creation and deletion
  //---------------------------------------------------------------------------------------
  /**
   * A test which verifies that when a secret is created and deleted, and another secret
   * with the same name is created later, listing versions of the current secret
   * does not include versions from the original secret.
   */
  @Test public void secretVersionManagement_createAndDelete() throws Exception {
    String name = "versionManagementSecret";
    String firstDescription = "the first secret with this name";
    String secondDescription = "the second secret with this name";

    // Create a secret
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name(name)
        .description(firstDescription)
        .content(encoder.encodeToString("secret version 1".getBytes(UTF_8)))
        .build());

    // Check that the secret's current versions are as expected
    List<SecretDetailResponseV2> versions = listVersions(name, 0, 1000);
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions.get(0).description()).isEqualTo(firstDescription);

    // Delete the secret and recreate it
    secretResourceTestHelper.deleteSeries(name);
   secretResourceTestHelper.create(CreateSecretRequestV2.builder()
        .name(name)
        .description(secondDescription)
        .content(encoder.encodeToString("secret version 2".getBytes(UTF_8)))
        .build());

    // Check that the original secret's versions were not retrieved
    versions = listVersions(name, 0, 1000);
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions.get(0).description()).isEqualTo(secondDescription);
  }

  //---------------------------------------------------------------------------------------
  // helper functions for tests
  //---------------------------------------------------------------------------------------

  private Response createGroup(String name) throws IOException {
    GroupResourceTestHelper groupResourceTestHelper = new GroupResourceTestHelper(mutualSslClient, mapper);
    return groupResourceTestHelper.create(CreateGroupRequestV2.builder().name(name).build());
  }

  private List<SecretDetailResponseV2> listVersions(String name, int versionIdx, int numVersions)
      throws IOException {
    Request get = clientRequest(
        format("/automation/v2/secrets/%s/versions?versionIdx=%d&numVersions=%d", name, versionIdx, numVersions)).get()
        .build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(),
        new TypeReference<List<SecretDetailResponseV2>>() {
        });
  }

  private void setCurrentVersion(SetSecretVersionRequestV2 request)
      throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post =
        clientRequest(format("/automation/v2/secrets/%s/setversion", request.name())).post(body)
            .build();
    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(201);
  }

  private Response renameSecret(String oldName, String newName) throws IOException {
    Request rename = clientRequest(
        String.format(
            "/automation/v2/secrets/%s/rename/%s",
            oldName,
            newName))
        .post(RequestBody.create(JSON, ""))
        .build();
    Response httpResponse = mutualSslClient.newCall(rename).execute();
    return httpResponse;
  }

  private void createSecret(String secretName) throws IOException {
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name(secretName)
        .content(encode("foo"))
        .build();
   secretResourceTestHelper.create(request);
  }

  private static String encode(String s) {
    return encoder.encodeToString(s.getBytes(UTF_8));
  }
}
