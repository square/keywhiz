package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.core.type.TypeReference;
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
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static keywhiz.TestClients.clientRequest;
import static keywhiz.client.KeywhizClient.JSON;
import static org.assertj.core.api.Assertions.assertThat;

public class GroupResourceTest {
  private static final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());

  OkHttpClient mutualSslClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  @Test public void createGroup_success() throws Exception {
    Response httpResponse = create(CreateGroupRequestV2.builder().name("group1").build());
    assertThat(httpResponse.code()).isEqualTo(201);
    URI location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/groups/group1");
  }

  @Test public void createGroup_duplicate() throws Exception {
    CreateGroupRequestV2 request = CreateGroupRequestV2.builder().name("group2").build();

    // Initial request OK
    create(request);

    // Duplicate request fails
    Response httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(409);
  }

  @Test public void groupInfo_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/groups/non-existent").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void groupInfo_simple() throws Exception {
    // Sample group
    create(CreateGroupRequestV2.builder().name("group3").description("desc").build());

    GroupDetailResponseV2 groupDetail = lookup("group3");
    assertThat(groupDetail.name()).isEqualTo("group3");
    assertThat(groupDetail.description()).isEqualTo("desc");
  }

  @Test public void groupInfo_withAssociations() throws Exception {
    // Sample group
    create(CreateGroupRequestV2.builder().name("group4").description("desc").build());

    // Sample client
    createClient("client4", "group4");

    GroupDetailResponseV2 groupDetail = lookup("group4");
    assertThat(groupDetail.name()).isEqualTo("group4");
    assertThat(groupDetail.description()).isEqualTo("desc");
  }

  @Test public void secretsForGroup() throws Exception {
    // Sample group
    create(CreateGroupRequestV2.builder().name("groupWithSecrets").description("desc").build());

    // Sample secret
    createSecret("groupWithSecrets", "test-secret");

    Set<SanitizedSecret> secrets = secretsInfo("groupWithSecrets");

    assertThat(secrets).hasSize(1);
    assertThat(secrets.iterator().next().name()).isEqualTo("test-secret");
  }

  @Test public void secretsWithGroupsForGroup() throws Exception {
    // Sample group
    create(CreateGroupRequestV2.builder().name("groupWithSharedSecrets").description("desc").build());
    create(CreateGroupRequestV2.builder().name("secondGroup").description("desc").build());

    // Sample secret
    createSecret("groupWithSharedSecrets", "shared-secret");
    assignSecret("secondGroup", "shared-secret");

    Set<SanitizedSecretWithGroups> secrets = secretsInfoWithGroups("groupWithSharedSecrets");

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

  @Test public void clientDetailForGroup() throws Exception {
    // Sample group
    create(CreateGroupRequestV2.builder().name("groupWithClients").description("desc").build());

    // Sample client
    createClient("test-client", "groupWithClients");

    Set<Client> clients = clientsInfo("groupWithClients");

    assertThat(clients).hasSize(1);
    assertThat(clients.iterator().next().getName()).isEqualTo("test-client");
  }

  @Test public void groupListing() throws Exception {
    Set<String> groupsBefore = listing();
    Set<String> expected = ImmutableSet.<String>builder()
        .addAll(groupsBefore)
        .add("group5")
        .build();

    create(CreateGroupRequestV2.builder().name("group5").build());
    assertThat(listing()).containsAll(expected);
  }

  @Test public void deleteGroup_success() throws Exception {
    create(CreateGroupRequestV2.builder().name("group6").build());

    Response httpResponse = delete("group6");
    assertThat(httpResponse.code()).isEqualTo(204);
  }

  @Test public void deleteGroup_notFound() throws Exception {
    Response httpResponse = delete("non-existent");
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  private Response createClient(String client, String... groups) throws IOException {
    ClientResourceTest clientResourceTest = new ClientResourceTest();
    clientResourceTest.mutualSslClient = mutualSslClient;
    Response response = clientResourceTest.create(
        CreateClientRequestV2.builder().name(client).groups(groups).build());
    assertThat(response.code()).isEqualTo(201);
    return response;
  }

  private Response createSecret(String group, String secret) throws IOException {
    SecretResourceTest secretResourceTest = new SecretResourceTest();
    secretResourceTest.mutualSslClient = mutualSslClient;
    Response response = secretResourceTest.create(
        CreateSecretRequestV2.builder().name(secret).content("test").groups(group).build());
    assertThat(response.code()).isEqualTo(201);
    return response;
  }

  private void assignSecret(String group, String secret) throws IOException {
    SecretResourceTest secretResourceTest = new SecretResourceTest();
    secretResourceTest.mutualSslClient = mutualSslClient;
    secretResourceTest.modifyGroups(secret,
        ModifyGroupsRequestV2.builder().addGroups(group).build());
  }

  Response create(CreateGroupRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest("/automation/v2/groups").post(body).build();
    return mutualSslClient.newCall(post).execute();
  }

  GroupDetailResponseV2 lookup(String group) throws IOException {
    Request get = clientRequest("/automation/v2/groups/" + group).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), GroupDetailResponseV2.class);
  }

  Set<SanitizedSecret> secretsInfo(String group) throws IOException {
    Request get = clientRequest("/automation/v2/groups/" + group + "/secrets").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<Set<SanitizedSecret>>(){});
  }

  Set<SanitizedSecretWithGroups> secretsInfoWithGroups(String group) throws IOException {
    Request get = clientRequest("/automation/v2/groups/" + group + "/secretsandgroups").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<Set<SanitizedSecretWithGroups>>(){});
  }

  Set<Client> clientsInfo(String group) throws IOException {
    Request get = clientRequest("/automation/v2/groups/" + group + "/clients").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<Set<Client>>(){});
  }

  Set<String> listing() throws IOException {
    Request get = clientRequest("/automation/v2/groups").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<Set<String>>(){});
  }

  Response delete(String name) throws IOException {
    Request delete = clientRequest("/automation/v2/groups/" + name).delete().build();
    return mutualSslClient.newCall(delete).execute();
  }
}
