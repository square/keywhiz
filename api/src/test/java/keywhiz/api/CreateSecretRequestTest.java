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

package keywhiz.api;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class CreateSecretRequestTest {
  @Test public void roundTripSerialization() throws Exception {
    CreateSecretRequest createSecretRequest = new CreateSecretRequest(
        "secretName",
        "secretOwner",
        "secretDesc",
        "YXNkZGFz",
        ImmutableMap.of("owner", "root"),
        0);

    assertThat(fromJson(asJson(createSecretRequest), CreateSecretRequest.class))
        .isEqualTo(createSecretRequest);
  }

  @Test public void requestWithoutOwnerSerializesCorrectly() throws Exception {
    CreateSecretRequest createSecretRequest = new CreateSecretRequest(
        "secretName",
        null,
        "secretDesc",
        "YXNkZGFz",
        ImmutableMap.of("owner", "root"),
        0);

    assertThat(fromJson(
        jsonFixture("fixtures/createSecretRequest.json"), CreateSecretRequest.class))
        .isEqualTo(createSecretRequest);
  }
  @Test public void requestWithOwnerSerializesCorrectly() throws Exception {
    CreateSecretRequest createSecretRequest = new CreateSecretRequest(
        "secretName",
        "secretOwner",
        "secretDesc",
        "YXNkZGFz",
        ImmutableMap.of("owner", "root"),
        0);

    assertThat(fromJson(
        jsonFixture("fixtures/createSecretRequestWithOwner.json"), CreateSecretRequest.class))
        .isEqualTo(createSecretRequest);
  }
}
