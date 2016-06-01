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

import com.google.common.collect.ImmutableMap;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.api.ApiDate;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretsDeliveryResourceIntegrationTest {
  OkHttpClient client;
  OkHttpClient noSecretsClient;
  OkHttpClient clientNoClientCert;

  SecretDeliveryResponse generalPassword;
  SecretDeliveryResponse databasePassword;
  SecretDeliveryResponse nobodyPgPassPassword;
  SecretDeliveryResponse nonExistentOwnerPass;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before
  public void setUp() throws Exception {
    client = TestClients.mutualSslClient();
    noSecretsClient = TestClients.noSecretsClient();
    clientNoClientCert = TestClients.unauthenticatedClient();

    generalPassword = SecretDeliveryResponse.fromSanitizedSecret(
        SanitizedSecret.fromSecret(
            new Secret(0, "General_Password", null, "YXNkZGFz",
                ApiDate.parse("2011-09-29T15:46:00.312Z"), null,
                ApiDate.parse("2011-09-29T15:46:00.312Z"), null, null, null, null)));
    databasePassword = SecretDeliveryResponse.fromSanitizedSecret(
        SanitizedSecret.fromSecret(
            new Secret(1, "Database_Password", null, "MTIzNDU=",
                ApiDate.parse("2011-09-29T15:46:00.232Z"), null,
                ApiDate.parse("2011-09-29T15:46:00.232Z"), null, null, null, null)));
    nobodyPgPassPassword = SecretDeliveryResponse.fromSanitizedSecret(
        SanitizedSecret.fromSecret(
            new Secret(2, "Nobody_PgPass", null,
                "c29tZWhvc3Quc29tZXBsYWNlLmNvbTo1NDMyOnNvbWVkYXRhYmFzZTptaXN0ZXJhd2Vzb21lOmhlbGwwTWNGbHkK",
                ApiDate.parse("2011-09-29T15:46:00.232Z"), null,
                ApiDate.parse("2011-09-29T15:46:00.232Z"), null,
                ImmutableMap.of("owner", "nobody", "mode", "0400"), null, null)));
    nonExistentOwnerPass = SecretDeliveryResponse.fromSanitizedSecret(
        SanitizedSecret.fromSecret(
            new Secret(3, "NonexistentOwner_Pass", null, "MTIzNDU=",
                ApiDate.parse("2011-09-29T15:46:00.232Z"), null,
                ApiDate.parse("2011-09-29T15:46:00.232Z"), null,
                ImmutableMap.of("owner", "NonExistent", "mode", "0400"), null, null)));
  }

  @Test
  public void returnsJsonArrayWhenUserHasMultipleSecrets() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/secrets"))
        .build();

    Response response = client.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);

    String responseString = response.body().string();
    assertThat(responseString)
        .contains(generalPassword.getName())
        .contains(databasePassword.getName())
        .contains(nobodyPgPassPassword.getName())
        .contains(nonExistentOwnerPass.getName());
  }

  @Test
  public void returnsJsonArray() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/secrets"))
        .build();

    Response response = client.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).startsWith("[").endsWith("]");
  }

  @Test
  public void returnsUnauthorizedWhenUnauthenticated() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/secrets"))
        .build();

    Response response = clientNoClientCert.newCall(get).execute();
    assertThat(response.code()).isEqualTo(401);
  }

  @Test
  public void returnsEmptyJsonArrayWhenUserHasNoSecrets() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/secrets"))
        .build();

    Response response = noSecretsClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).isEqualTo("[]");
  }
}
