/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package keywhiz.service.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.dropwizard.jackson.Jackson;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.ApiDate;
import keywhiz.api.BatchSecretRequest;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Secret;
import keywhiz.client.KeywhizClient;
import keywhiz.testing.JsonHelpers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class BatchSecretDeliveryResourceIntegrationTest {
    ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
    OkHttpClient client;
    Secret generalPassword, databasePassword;
    KeywhizClient keywhizClient;

    @ClassRule
    public static final RuleChain chain = IntegrationTestRule.rule();

    @Before
    public void setUp() throws Exception {
        client = TestClients.mutualSslClient();
        keywhizClient = TestClients.keywhizClient();
        generalPassword = new Secret(0, "General_Password", null, () -> "YXNkZGFz", "",
                ApiDate.parse("2011-09-29T15:46:00Z"), null,
                ApiDate.parse("2011-09-29T15:46:00Z"), null, null, "upload",
                null, 0, 1L, ApiDate.parse("2011-09-29T15:46:00Z"), null);

        databasePassword = new Secret(1, "Database_Password", null, () -> "MTIzNDU=", "",
                ApiDate.parse("2011-09-29T15:46:00.232Z"), null,
                ApiDate.parse("2011-09-29T15:46:00.232Z"), null,
                null, null, null, 0, 2L,
                ApiDate.parse("2011-09-29T15:46:00.312Z"), null);
    }

    @Test
    public void returnsSecretWhenAllowed() throws Exception {
        BatchSecretRequest request = BatchSecretRequest.create(ImmutableList.of("General_Password"));

        String body = mapper.writeValueAsString(request);
        Request post = new Request.Builder()
                .post(RequestBody.create(KeywhizClient.JSON, body))
                .url(testUrl("/batchsecret"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .build();

        Response response = client.newCall(post).execute();
        assertThat(response.code()).isEqualTo(200);
    }

    @Test
    public void returnsMultipleSecretWhenAllowed() throws Exception {
        BatchSecretRequest request = BatchSecretRequest.create(ImmutableList.of("General_Password", "Database_Password"));

        String body = mapper.writeValueAsString(request);
        Request post = new Request.Builder()
                .post(RequestBody.create(KeywhizClient.JSON, body))
                .url(testUrl("/batchsecret"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .build();

        Response response = client.newCall(post).execute();
        assertThat(response.code()).isEqualTo(200);

        ImmutableList<SecretDeliveryResponse> parsedResponse = JsonHelpers.fromJson(response.body().string(), ImmutableList.<SecretDeliveryResponse>of().getClass());

        assertThat(parsedResponse.size() == 2);
        assertThat(parsedResponse.contains(generalPassword));
        assertThat(parsedResponse.contains(databasePassword));
    }

    @Test
    public void returnsMultipleSecretWhenAllowedOnlyUnique() throws Exception {
        BatchSecretRequest request = BatchSecretRequest.create(ImmutableList.of("Database_Password", "General_Password", "Database_Password", "General_Password"));

        String body = mapper.writeValueAsString(request);
        Request post = new Request.Builder()
                .post(RequestBody.create(KeywhizClient.JSON, body))
                .url(testUrl("/batchsecret"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .build();

        Response response = client.newCall(post).execute();
        assertThat(response.code()).isEqualTo(200);

        ImmutableList<SecretDeliveryResponse> parsedResponse = JsonHelpers.fromJson(response.body().string(), ImmutableList.<SecretDeliveryResponse>of().getClass());

        assertThat(parsedResponse.size() == 2);
        assertThat(parsedResponse.contains(generalPassword));
        assertThat(parsedResponse.contains(databasePassword));
    }

    @Test
    public void returns500WhenSecretUnspecified() throws Exception {
        BatchSecretRequest request = BatchSecretRequest.create(ImmutableList.<String>of());

        String body = mapper.writeValueAsString(request);
        Request post = new Request.Builder()
                .post(RequestBody.create(KeywhizClient.JSON, body))
                .url(testUrl("/batchsecret"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .build();

        Response response = client.newCall(post).execute();
        assertThat(response.code()).isEqualTo(500);
    }

    @Test
    public void returnsUnauthorizedWhenDenied() throws Exception {
        BatchSecretRequest request = BatchSecretRequest.create(ImmutableList.of("Hacking_Password"));

        String body = mapper.writeValueAsString(request);
        Request post = new Request.Builder()
                .post(RequestBody.create(KeywhizClient.JSON, body))
                .url(testUrl("/batchsecret"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .build();

        Response response = client.newCall(post).execute();
        assertThat(response.code()).isEqualTo(403);
    }


}
