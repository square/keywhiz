package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.automation.v2.CreateClientRequestV2;
import keywhiz.api.automation.v2.CreateGroupRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.GroupDetailResponseV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SanitizedSecretWithGroups;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static keywhiz.TestClients.clientRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class GroupResourceTest {
  private static final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());

  OkHttpClient mutualSslClient;
  GroupResourceTestHelper groupResourceTestHelper;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
    groupResourceTestHelper = new GroupResourceTestHelper(mutualSslClient, mapper);
  }

  @Test public void createGroup_success() throws Exception {
    Response httpResponse = groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("group1").build());
    assertThat(httpResponse.code()).isEqualTo(201);
    URI location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/groups/group1");
  }

  @Test public void createGroup_duplicate() throws Exception {
    CreateGroupRequestV2 request = CreateGroupRequestV2.builder().name("group2").build();

    // Initial request OK
    groupResourceTestHelper.create(request);

    // Duplicate request fails
    Response httpResponse = groupResourceTestHelper.create(request);
    assertThat(httpResponse.code()).isEqualTo(409);
  }

  @Test public void groupInfo_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/groups/non-existent").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void groupInfo_simple() throws Exception {
    // Sample group
    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("group3").description("desc").build());

    GroupDetailResponseV2 groupDetail = groupResourceTestHelper.lookup("group3");
    assertThat(groupDetail.name()).isEqualTo("group3");
    assertThat(groupDetail.description()).isEqualTo("desc");
  }

  @Test public void groupInfo_withAssociations() throws Exception {
    // Sample group
    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("group4").description("desc").build());

    // Sample client
    createClient("client4", "group4");

    GroupDetailResponseV2 groupDetail = groupResourceTestHelper.lookup("group4");
    assertThat(groupDetail.name()).isEqualTo("group4");
    assertThat(groupDetail.description()).isEqualTo("desc");
  }

  @Test public void secretsForGroup() throws Exception {
    // Sample group
    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("groupWithSecrets").description("desc").build());

    // Sample secret
    createSecret("groupWithSecrets", "test-secret");

    Set<SanitizedSecret> secrets = groupResourceTestHelper.secretsInfo("groupWithSecrets");

    assertThat(secrets).hasSize(1);
    assertThat(secrets.iterator().next().name()).isEqualTo("test-secret");
  }

  @Test public void secretsWithGroupsForGroup() throws Exception {
    // Sample group
    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("groupWithSharedSecrets").description("desc").build());
    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("secondGroup").description("desc").build());

    // Sample secret
    createSecret("groupWithSharedSecrets", "shared-secret");
    assignSecret("secondGroup", "shared-secret");

    Set<SanitizedSecretWithGroups> secrets = groupResourceTestHelper.secretsInfoWithGroups("groupWithSharedSecrets"
    );

    assertThat(secrets).hasSize(1);
    SanitizedSecretWithGroups secretWithGroups = secrets.iterator().next();
    assertThat(secretWithGroups.secret().name()).isEqualTo("shared-secret");
    Set<String> groupNames = secretWithGroups.groups()
        .stream()
        .map(Group::getName)
        .collect(Collectors.toUnmodifiableSet());
    assertThat(groupNames).hasSize(2);
    assertThat(groupNames.contains("groupWithSharedSecrets"));
    assertThat(groupNames.contains("secondGroup"));
  }

  @Test public void ownedSecretsWithGroupsForGroup() throws Exception {
    // Sample group
    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("groupWithSharedSecrets1").description("desc").build());
    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("secondGroup1").description("desc").build());

    // Sample secret
    createSecret("groupWithSharedSecrets1", "shared-secret1", "groupWithSharedSecrets1");
    assignSecret("secondGroup1", "shared-secret1");

    Set<SanitizedSecretWithGroups> secrets = groupResourceTestHelper.ownedSecretsInfoWithGroups("groupWithSharedSecrets1");

    SanitizedSecretWithGroups secretWithGroups = secrets.iterator().next();
    assertEquals("shared-secret1", secretWithGroups.secret().name());

    Set<String> groupNames = secretWithGroups.groups()
        .stream()
        .map(Group::getName)
        .collect(Collectors.toUnmodifiableSet());
    assertEquals(Set.of("groupWithSharedSecrets1", "secondGroup1"), groupNames);
  }

  @Test public void clientDetailForGroup() throws Exception {
    // Sample group
    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("groupWithClients").description("desc").build());

    // Sample client
    createClient("test-client", "groupWithClients");

    Set<Client> clients = groupResourceTestHelper.clientsInfo("groupWithClients");

    assertThat(clients).hasSize(1);
    assertThat(clients.iterator().next().getName()).isEqualTo("test-client");
  }

  @Test public void groupListing() throws Exception {
    Set<String> groupsBefore = groupResourceTestHelper.listing();
    Set<String> expected = ImmutableSet.<String>builder()
        .addAll(groupsBefore)
        .add("group5")
        .build();

    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("group5").build());
    assertThat(groupResourceTestHelper.listing()).containsAll(expected);
  }

  @Test public void deleteGroup_success() throws Exception {
    groupResourceTestHelper.create(CreateGroupRequestV2.builder().name("group6").build());

    Response httpResponse = groupResourceTestHelper.delete("group6");
    assertThat(httpResponse.code()).isEqualTo(204);
  }

  @Test public void deleteGroup_notFound() throws Exception {
    Response httpResponse = groupResourceTestHelper.delete("non-existent");
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  private Response createClient(String client, String... groups) throws IOException {
    ClientResourceTestHelper clientResourceTestHelper = new ClientResourceTestHelper(mutualSslClient, mapper);
    Response response = clientResourceTestHelper.create(
        CreateClientRequestV2.builder().name(client).groups(groups).build());
    assertThat(response.code()).isEqualTo(201);
    return response;
  }

  private Response createSecret(String group, String secret, String owner) throws IOException {
    SecretResourceTestHelper secretResourceTestHelper = new SecretResourceTestHelper(mutualSslClient, mapper);
    Response response = secretResourceTestHelper.create(
        CreateSecretRequestV2.builder().name(secret).content("test").groups(group).owner(owner).build());
    assertThat(response.code()).isEqualTo(201);
    return response;
  }

  private Response createSecret(String group, String secret) throws IOException {
    return createSecret(group, secret, null);
  }

  private void assignSecret(String group, String secret) throws IOException {
    SecretResourceTestHelper secretResourceTestHelper = new SecretResourceTestHelper(mutualSslClient, mapper);
    secretResourceTestHelper.modifyGroups(secret,
        ModifyGroupsRequestV2.builder().addGroups(group).build());
  }
}
