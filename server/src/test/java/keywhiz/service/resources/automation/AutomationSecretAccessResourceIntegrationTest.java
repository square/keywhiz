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

package keywhiz.service.resources.automation;

import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class AutomationSecretAccessResourceIntegrationTest {
  OkHttpClient mutualSslClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  @Test public void allowAccess() throws Exception {
    //Allow "Web" to access "Hacking_Password"
    Request put = new Request.Builder()
        .put(RequestBody.create(MediaType.parse("text/plain"), ""))
        .url(testUrl("/automation/secrets/738/groups/918"))
        .build();

    Response response = mutualSslClient.newCall(put).execute();
    assertThat(response.code()).isEqualTo(200);
  }

  @Test public void revokeAccess() throws Exception {
    //Revoke "Database_password" from "Web"
    Request delete = new Request.Builder()
        .delete()
        .url(testUrl("/automation/secrets/739/groups/918"))
        .build();

    Response response = mutualSslClient.newCall(delete).execute();
    assertThat(response.code()).isEqualTo(200);
  }
}
