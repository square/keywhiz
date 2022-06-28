package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.automation.v2.ClientDetailResponseV2;
import keywhiz.api.automation.v2.CreateClientRequestV2;
import keywhiz.api.automation.v2.CreateGroupRequestV2;
import keywhiz.api.automation.v2.ModifyClientRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
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
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static keywhiz.TestClients.clientRequest;
import static keywhiz.client.KeywhizClient.JSON;
import static org.assertj.core.api.Assertions.assertThat;

public class ClientResourceTest {
  private static final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());

  OkHttpClient mutualSslClient;
  ClientResourceTestHelper clientResourceTestHelper;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
    clientResourceTestHelper = new ClientResourceTestHelper(mutualSslClient, mapper);

  }

  @Test public void createClient_success() throws Exception {
    Response httpResponse = clientResourceTestHelper.create(CreateClientRequestV2.builder()
        .name("client1")
        .description("description")
        .spiffeId("spiffe//example.org/client1")
        .build());
    assertThat(httpResponse.code()).isEqualTo(201);
    URI location = URI.create(Objects.requireNonNull(httpResponse.header(LOCATION)));
    assertThat(location.getPath()).isEqualTo("/automation/v2/clients/client1");

    ClientDetailResponseV2 clientDetail = clientResourceTestHelper.lookup("client1");
    assertThat(clientDetail.name()).isEqualTo("client1");
    assertThat(clientDetail.description()).isEqualTo("description");
    assertThat(clientDetail.createdBy()).isEqualTo(clientDetail.updatedBy()).isEqualTo("client");
    assertThat(clientDetail.spiffeId()).isEqualTo("spiffe//example.org/client1");
  }

  @Test public void createClient_duplicate() throws Exception {
    CreateClientRequestV2 request = CreateClientRequestV2.builder().name("client2").build();

    // Initial request OK
    clientResourceTestHelper.create(request);

    // Duplicate request fails
    Response httpResponse = clientResourceTestHelper.create(request);
    assertThat(httpResponse.code()).isEqualTo(409);

    // The client was created by the first request
    ClientDetailResponseV2 clientDetail = clientResourceTestHelper.lookup("client2");
    assertThat(clientDetail.name()).isEqualTo("client2");
    assertThat(clientDetail.description()).isEmpty();
    assertThat(clientDetail.createdBy()).isEqualTo(clientDetail.updatedBy()).isEqualTo("client");
    assertThat(clientDetail.spiffeId()).isEmpty();
  }

  @Test public void clientInfo_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/clients/non-existent").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void clientInfo_groupExists() throws Exception {
    // Sample client
    clientResourceTestHelper.create(CreateClientRequestV2.builder().name("client3").build());

    ClientDetailResponseV2 clientDetail = clientResourceTestHelper.lookup("client3");
    assertThat(clientDetail.name()).isEqualTo("client3");
    assertThat(clientDetail.description()).isEmpty();
    assertThat(clientDetail.createdBy()).isEqualTo(clientDetail.updatedBy()).isEqualTo("client");
  }

  @Test public void clientListing() throws Exception {
    Set<String> clientsBefore = clientResourceTestHelper.listing();
    Set<String> expected = ImmutableSet.<String>builder()
        .addAll(clientsBefore)
        .add("client4")
        .build();

    clientResourceTestHelper.create(CreateClientRequestV2.builder().name("client4").build());
    assertThat(clientResourceTestHelper.listing()).containsAll(expected);
  }

  @Test public void clientDelete_success() throws Exception {
    // Sample client
    clientResourceTestHelper.create(CreateClientRequestV2.builder().name("to-delete").build());

    // Deleting is successful
    Response httpResponse = delete("to-delete");
    assertThat(httpResponse.code()).isEqualTo(204);

    // Deleting again produces not-found
    httpResponse = delete("to-delete");
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void clientDelete_notFound() throws Exception {
    Response httpResponse = delete("non-existent");
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void clientGroupsListing_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/clients/non-existent/groups").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void clientGroupsListing_nonExistingGroup() throws Exception {
    clientResourceTestHelper.create(CreateClientRequestV2.builder().name("client5").groups("non-existent").build()
    );
    assertThat(groupListing("client5")).isEmpty();
  }

  @Test public void clientGroupsListing_groupExists() throws Exception {
    // Sample group and client
    createGroup("group6");
    clientResourceTestHelper.create(CreateClientRequestV2.builder().name("client6").groups("group6").build()
    );

    assertThat(groupListing("client6")).containsOnly("group6");
  }

  @Test public void clientSecretsListing_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/clients/non-existent/secrets").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Ignore
  @Test public void clientSecretsListing_secretExists() throws Exception {
    createGroup("group7");
    // TODO: create secret 'secret7' in group 'group7'
    clientResourceTestHelper.create(CreateClientRequestV2.builder().name("client7").groups("group7").build()
    );

    assertThat(secretListing("client7")).containsOnly("secret7");
  }

  @Test public void modifyClientGroups_notFound() throws Exception {
    ModifyGroupsRequestV2 request = ModifyGroupsRequestV2.builder().build();
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request put = clientRequest("/automation/v2/clients/non-existent/groups").put(body).build();
    Response httpResponse = mutualSslClient.newCall(put).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void modifyClientGroups_success() throws Exception {
    // Create sample client and groups
    createGroup("group8a");
    createGroup("group8b");
    createGroup("group8c");
    clientResourceTestHelper.create(CreateClientRequestV2.builder().name("client8").groups("group8a", "group8b").build()
    );

    // Modify client
    ModifyGroupsRequestV2 request = ModifyGroupsRequestV2.builder()
        .addGroups("group8c", "non-existent1")
        .removeGroups("group8a", "non-existent2")
        .build();
    List<String> groups = clientResourceTestHelper.modifyGroups("client8", request);
    assertThat(groups).containsOnly("group8b", "group8c");
  }

  @Test public void modifyClient_notFound() throws Exception {
    ModifyClientRequestV2 request = ModifyClientRequestV2.forName("non-existent");
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest("/automation/v2/clients/non-existent").post(body).build();
    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Ignore
  @Test public void modifyClient_success() throws Exception {
    // Create sample client
    clientResourceTestHelper.create(CreateClientRequestV2.builder().name("client9").build());
    ClientDetailResponseV2 originalClient = clientResourceTestHelper.lookup("client9");

    // Modify client
    ModifyClientRequestV2 request = ModifyClientRequestV2.forName("client9b");
    ClientDetailResponseV2 clientDetail = clientResourceTestHelper.modify("client9", request);
    assertThat(clientDetail.name()).isEqualTo("client9b");
    assertThat(clientDetail).isEqualToIgnoringGivenFields(originalClient, "name", "updateDate");
    assertThat(clientDetail.updatedAtSeconds()).isGreaterThan(originalClient.updatedAtSeconds());
  }

  private Response createGroup(String name) throws IOException {
    GroupResourceTestHelper groupResourceTestHelper = new GroupResourceTestHelper(mutualSslClient, mapper);
    return groupResourceTestHelper.create(CreateGroupRequestV2.builder().name(name).build());
  }

  Response delete(String name) throws IOException {
    Request delete = clientRequest("/automation/v2/clients/" + name).delete().build();
    return mutualSslClient.newCall(delete).execute();
  }

  List<String> groupListing(String client) throws IOException {
    Request get = clientRequest(format("/automation/v2/clients/%s/groups", client)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>() {
    });
  }

  List<String> secretListing(String client) throws IOException {
    Request get = clientRequest(format("/automation/v2/clients/%s/secrets", client)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>(){});
  }
}
