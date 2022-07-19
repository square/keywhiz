package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import keywhiz.api.automation.v2.CreateGroupRequestV2;
import keywhiz.api.automation.v2.GroupDetailResponseV2;
import keywhiz.api.model.Client;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SanitizedSecretWithGroups;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static keywhiz.TestClients.clientRequest;
import static keywhiz.client.KeywhizClient.JSON;
import static org.assertj.core.api.Assertions.assertThat;

public class GroupResourceTestHelper {
  private OkHttpClient mutualSslClient;
  private ObjectMapper mapper;

  GroupResourceTestHelper(OkHttpClient mutualSslClient, ObjectMapper mapper) {
    this.mutualSslClient = mutualSslClient;
    this.mapper = mapper;
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

  Set<SanitizedSecretWithGroups> ownedSecretsInfoWithGroups(String group) throws IOException {
    Request get = clientRequest("/automation/v2/groups/" + group + "/ownedsecretsandgroups").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<Set<SanitizedSecretWithGroups>>(){});
  }
}
