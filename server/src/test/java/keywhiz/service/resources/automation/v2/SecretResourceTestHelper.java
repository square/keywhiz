package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import keywhiz.api.automation.v2.CreateOrUpdateSecretRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.automation.v2.PartialUpdateSecretRequestV2;
import keywhiz.api.automation.v2.SecretContentsRequestV2;
import keywhiz.api.automation.v2.SecretContentsResponseV2;
import keywhiz.api.automation.v2.SecretDetailResponseV2;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SanitizedSecretWithGroups;
import keywhiz.api.model.SanitizedSecretWithGroupsListAndCursor;
import keywhiz.api.model.SecretRetrievalCursor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static java.lang.String.format;
import static keywhiz.TestClients.clientRequest;
import static keywhiz.client.KeywhizClient.JSON;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretResourceTestHelper {
  private OkHttpClient mutualSslClient;
  private ObjectMapper mapper;

  SecretResourceTestHelper(OkHttpClient mutualSslClient, ObjectMapper mapper) {
    this.mutualSslClient = mutualSslClient;
    this.mapper = mapper;
  }

  Response create(CreateSecretRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest("/automation/v2/secrets").post(body).build();
    return mutualSslClient.newCall(post).execute();
  }

  Response backfillExpiration(String name, List<String> passwords) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(passwords));
    Request post = clientRequest(String.format("/automation/v2/secrets/%s/backfill-expiration", name)).post(body).build();
    return mutualSslClient.newCall(post).execute();
  }

  Response partialUpdate(PartialUpdateSecretRequestV2 request, String name) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest(format("/automation/v2/secrets/%s/partialupdate", name)).post(body).build();
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
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>() {
    });
  }

  List<String> listBatch(int idx, int num, boolean newestFirst) throws IOException {
    Request get = clientRequest(String.format("/automation/v2/secrets?idx=%d&num=%d&newestFirst=%s", idx, num, newestFirst)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>() {
    });
  }

  List<SanitizedSecret> listingV2() throws IOException {
    Request get = clientRequest("/automation/v2/secrets/v2").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<SanitizedSecret>>() {
    });
  }

  List<SanitizedSecret> listBatchV2(int idx, int num, boolean newestFirst) throws IOException {
    Request get = clientRequest(String.format("/automation/v2/secrets/v2?idx=%d&num=%d&newestFirst=%s", idx, num, newestFirst)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<SanitizedSecret>>() {
    });
  }

  List<String> listExpiring(Long time, String groupName) throws IOException {
    String requestURL = "/automation/v2/secrets/expiring/";
    if (time != null && time > 0) {
      requestURL += time.toString() + '/';
    }
    if (groupName != null && groupName.length() > 0) {
      requestURL += groupName;
    }
    Request get = clientRequest(requestURL).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>() {
    });
  }

  List<SanitizedSecret> listExpiringV2(Long time, String groupName) throws IOException {
    String requestURL = "/automation/v2/secrets/expiring/v2/";
    if (time != null && time > 0) {
      requestURL += time.toString() + '/';
    }
    if (groupName != null && groupName.length() > 0) {
      requestURL += groupName;
    }
    Request get = clientRequest(requestURL).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<SanitizedSecret>>() {
    });
  }

  List<SanitizedSecretWithGroups> listExpiringV3(Long maxTime) throws IOException {
    String requestURL = "/automation/v2/secrets/expiring/v3/";
    if (maxTime != null && maxTime > 0) {
      requestURL += maxTime.toString() + '/';
    }
    Request get = clientRequest(requestURL).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<SanitizedSecretWithGroups>>() {
    });
  }

  SanitizedSecretWithGroupsListAndCursor listExpiringV4(Long minTime, Long maxTime, Integer limit,
      SecretRetrievalCursor cursor) throws IOException {
    String requestURL = "/automation/v2/secrets/expiring/v4";
    // This should probably be replaced with a proper query library
    if (minTime != null || maxTime != null || limit != null || cursor != null) {
      requestURL += "?";

      boolean firstQueryParam = true;
      if (minTime != null) {
        // Redundant, but avoids copy-paste errors if another query param is added.
        if(!firstQueryParam) {
          requestURL += "&";
        }
        requestURL += "minTime=";
        requestURL += minTime.toString();
        firstQueryParam = false;
      }

      if (maxTime != null) {
        if(!firstQueryParam) {
          requestURL += "&";
        }
        requestURL += "maxTime=";
        requestURL += maxTime.toString();
        firstQueryParam = false;
      }

      if (limit != null) {
        if(!firstQueryParam) {
          requestURL += "&";
        }
        requestURL += "limit=";
        requestURL += limit.toString();
        firstQueryParam = false;
      }

      if (cursor != null) {
        if(!firstQueryParam) {
          requestURL += "&";
        }
        requestURL += "cursor=";
        requestURL += SecretRetrievalCursor.toUrlEncodedString(cursor);
      }
    }

    Request get = clientRequest(requestURL).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), SanitizedSecretWithGroupsListAndCursor.class);
  }

  Optional<SecretDetailResponseV2> getSecret(String name) throws IOException {
    Request get = clientRequest("/automation/v2/secrets/" + name).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    if (httpResponse.code() == 200) {
      return Optional.of(mapper.readValue(httpResponse.body().byteStream(), SecretDetailResponseV2.class));
    } else if (httpResponse.code() == 404) {
      return Optional.empty();
    } else {
      throw new RuntimeException(
          String.format(
              "Unexpected response code %s while fetching secret %s",
              httpResponse.code(),
              name));
    }
  }

  SecretDetailResponseV2 lookup(String name) throws IOException {
    Request get = clientRequest("/automation/v2/secrets/" + name).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), SecretDetailResponseV2.class);
  }

  SanitizedSecret lookupSanitizedSecret(String name) throws IOException {
    Request get = clientRequest(format("/automation/v2/secrets/%s/sanitized", name)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), SanitizedSecret.class);
  }

  SecretContentsResponseV2 contents(SecretContentsRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request get = clientRequest("/automation/v2/secrets/request/contents").post(body).build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), SecretContentsResponseV2.class);
  }

  List<String> groupsListing(String name) throws IOException {
    Request get = clientRequest(format("/automation/v2/secrets/%s/groups", name)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>() {
    });
  }

  List<String> modifyGroups(String name, ModifyGroupsRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request put = clientRequest(format("/automation/v2/secrets/%s/groups", name)).put(body).build();
    Response httpResponse = mutualSslClient.newCall(put).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>() {
    });
  }

  Response deleteSeries(String name) throws IOException {
    Request delete = clientRequest("/automation/v2/secrets/" + name).delete().build();
    return mutualSslClient.newCall(delete).execute();
  }
}
