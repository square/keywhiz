package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import keywhiz.api.automation.v2.ClientDetailResponseV2;
import keywhiz.api.automation.v2.CreateClientRequestV2;
import keywhiz.api.automation.v2.ModifyClientRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static java.lang.String.format;
import static keywhiz.TestClients.clientRequest;
import static keywhiz.client.KeywhizClient.JSON;
import static org.assertj.core.api.Assertions.assertThat;

public class ClientResourceTestHelper {
  private OkHttpClient mutualSslClient;
  private ObjectMapper mapper;

  ClientResourceTestHelper(OkHttpClient mutualSslClient, ObjectMapper mapper) {
    this.mutualSslClient = mutualSslClient;
    this.mapper = mapper;
  }

  Response create(CreateClientRequestV2 request) throws
      IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest("/automation/v2/clients").post(body).build();
    return mutualSslClient.newCall(post).execute();
  }

  ClientDetailResponseV2 lookup(String client) throws IOException {
    Request get = clientRequest("/automation/v2/clients/" + client).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), ClientDetailResponseV2.class);
  }

  Set<String> listing() throws IOException {
    Request get = clientRequest("/automation/v2/clients").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<Set<String>>(){});
  }

  ClientDetailResponseV2 modify(String client, ModifyClientRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest("/automation/v2/clients/" + client).post(body).build();
    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), ClientDetailResponseV2.class);
  }

  List<String> modifyGroups(String client, ModifyGroupsRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request put = clientRequest(format("/automation/v2/clients/%s/groups", client)).put(body).build();
    Response httpResponse = mutualSslClient.newCall(put).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>(){});
  }
}
