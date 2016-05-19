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
package keywhiz.service.resources.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import io.dropwizard.jackson.Jackson;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.SecretDetailResponse;
import keywhiz.client.KeywhizClient;
import keywhiz.commands.DbSeedCommand;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static java.lang.String.format;
import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class MigrateResourceIntegrationTest {
  ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  OkHttpClient mutualTlsClient;
  KeywhizClient keywhizClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualTlsClient = TestClients.mutualSslClient();
    keywhizClient = TestClients.keywhizClient();
  }

  @Test public void replaceIntermediateCertificate() throws IOException {
    // Use admin api to create a secret and grant it to client
    keywhizClient.login(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword.toCharArray());
    byte[] file1 = Files.readAllBytes(new File("src/test/resources/fixtures/a.crt").toPath());
    SecretDetailResponse secretResponse = keywhizClient.createSecret("foo.crt", "a secret", file1, false, ImmutableMap.of());
    keywhizClient.grantSecretToGroupByIds(secretResponse.id, 917);

    // Hit migrate
    Request migrateRequest = new Request.Builder()
        .get()
        .url(testUrl(format("/admin/migrate/replace-intermediate-certificate?id=%d", secretResponse.id)))
        .build();
    mutualTlsClient.newCall(migrateRequest).execute();


    // Read back the value.
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/secret/foo.crt"))
        .build();
    Response response = mutualTlsClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
    SecretDeliveryResponse r = mapper.readValue(response.body().string(), SecretDeliveryResponse.class);
    byte[] content = BaseEncoding.base64().decode(r.getSecret());

    byte[] file2 = Files.readAllBytes(new File("src/test/resources/fixtures/b.crt").toPath());
    assertThat(content).isEqualTo(file2);
  }
}
