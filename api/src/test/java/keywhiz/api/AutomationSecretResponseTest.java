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

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;
import org.junit.Test;

import static keywhiz.api.model.Secret.decodedLength;
import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class AutomationSecretResponseTest {

  private static final ImmutableMap<String, String> metadata =
      ImmutableMap.of("key1", "value1", "key2", "value2");
  private static final ApiDate NOW = ApiDate.now();
  private static final Secret secret =
      new Secret(
          0,
          "name",
          "owner",
          null,
          () -> "YWJj",
          "checksum",
          NOW,
          null,
          NOW,
          null,
          metadata,
          "upload",
          null,
          1136214245,
          null,
          null,
          null);

  @Test
  public void setsLength() {
    AutomationSecretResponse response = AutomationSecretResponse.fromSecret(secret,
        ImmutableList.<Group>of());
    assertThat(response.secretLength()).isEqualTo(decodedLength("YWJj")).isEqualTo(3);
  }

  @Test
  public void hasMetaData() {
    AutomationSecretResponse response = AutomationSecretResponse.fromSecret(secret,
        ImmutableList.<Group>of());
    assertThat(response.metadata()).contains(entry("key2", "value2"), entry("key1", "value1"));
  }

  @Test
  public void roundTrip() throws Exception {
    AutomationSecretResponse automationSecretResponse = AutomationSecretResponse.create(
        0,
        "Database_Password",
        "owner",
        "YXNkZGFz",
        ApiDate.parse("2011-09-29T15:46:00.000Z"),
        ImmutableMap.of(),
        ImmutableList.of(),
        1136214245);
    assertThat(
        fromJson(asJson(automationSecretResponse), AutomationSecretResponse.class)).isEqualTo(
        automationSecretResponse);
  }

  @Test
  public void responseWithoutOwnerDeserializesCorrectly() throws Exception {
    AutomationSecretResponse automationSecretResponse = AutomationSecretResponse.create(
        0,
        "Database_Password",
        null,
        "YXNkZGFz",
        ApiDate.parse("2011-09-29T15:46:00.000Z"),
        ImmutableMap.of(),
        ImmutableList.of(),
        1136214245);
    assertThat(fromJson(jsonFixture("fixtures/automationSecretResponse.json"),
        AutomationSecretResponse.class)).isEqualTo(automationSecretResponse);
  }

  @Test
  public void responseWithOwnerDeserializesCorrectly() throws Exception {
    AutomationSecretResponse automationSecretResponse = AutomationSecretResponse.create(
        0,
        "Database_Password",
        "owner",
        "YXNkZGFz",
        ApiDate.parse("2011-09-29T15:46:00.000Z"),
        ImmutableMap.of(),
        ImmutableList.of(),
        1136214245);
    assertThat(fromJson(jsonFixture("fixtures/automationSecretResponseWithOwner.json"),
        AutomationSecretResponse.class)).isEqualTo(automationSecretResponse);
  }

  // The keys in the metadata field are removed from that field and serialized along
  // with the rest of the fields, so a SecretDeliveryResponse that includes metadata
  // cannot be serialized and deserialized successfully.
  @Test public void unpacksMetadata() {
    AutomationSecretResponse automationSecretResponseWithMetadata = AutomationSecretResponse.create(
        66,
        "Nobody_PgPass",
        "owner",
        "YXNkZGFz",
        ApiDate.parse("2011-09-29T15:46:00.000Z"),
        ImmutableMap.of("mode", "0400", "owner", "nobody"),
        ImmutableList.of(),
        1136214245);

    assertThatThrownBy(() ->
        fromJson(jsonFixture("fixtures/automationSecretResponseWithMetadata.json"),
            AutomationSecretResponse.class))
        .isInstanceOf(UnrecognizedPropertyException.class)
        .hasMessageContaining("Unrecognized field \"mode\"");

    assertThatThrownBy(() -> fromJson(asJson(automationSecretResponseWithMetadata),
        AutomationSecretResponse.class))
        .isInstanceOf(UnrecognizedPropertyException.class)
        .hasMessageContaining("Unrecognized field \"mode\"");
  }
}
