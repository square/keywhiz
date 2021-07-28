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
import com.google.common.collect.ImmutableMap;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;
import org.junit.Test;

import static keywhiz.api.model.Secret.decodedLength;
import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;

public class SecretDeliveryResponseTest {
  private static final ImmutableMap<String, String> metadata =
      ImmutableMap.of("key1", "value1", "key2", "value2");
  private static final ApiDate NOW = ApiDate.now();
  private static final Secret secret = new Secret(
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
      0,
      null,
      NOW,
      null);

  private static final SanitizedSecret sanitizedSecret =
      SanitizedSecret.of(
          0,
          "name",
          "owner",
          null,
          "checksum",
          NOW,
          null,
          NOW,
          null,
          metadata,
          "upload",
          null,
          0,
          null,
          NOW,
          null);

  @Test
  public void setsLength() {
    SecretDeliveryResponse response = SecretDeliveryResponse.fromSecret(secret);
    assertThat(response.getSecretLength()).isEqualTo(decodedLength("YWJj")).isEqualTo(3);

    // SanitizedSecrets do not contain content, so the content length is 0
    SecretDeliveryResponse sanitizedResponse =
        SecretDeliveryResponse.fromSanitizedSecret(sanitizedSecret);
    assertThat(sanitizedResponse.getSecretLength()).isEqualTo(0);
  }

  @Test
  public void hasMetaData() {
    SecretDeliveryResponse response = SecretDeliveryResponse.fromSecret(secret);
    assertThat(response.getMetadata()).contains(entry("key2", "value2"), entry("key1", "value1"));

    SecretDeliveryResponse sanitizedResponse =
        SecretDeliveryResponse.fromSanitizedSecret(sanitizedSecret);
    assertThat(sanitizedResponse.getMetadata()).contains(entry("key2", "value2"),
        entry("key1", "value1"));
  }

  @Test
  public void deserializesCorrectly() throws Exception {
    String secret = "YXNkZGFz";

    SecretDeliveryResponse secretDeliveryResponse = new SecretDeliveryResponse(
        "Database_Password",
        secret,
        decodedLength(secret),
        "database_checksum",
        ApiDate.parse("2011-09-29T15:46:00.000Z"),
        ApiDate.parse("2012-09-29T16:34:00.000Z"),
        null);
    assertThat(
        fromJson(jsonFixture("fixtures/secretDeliveryResponse.json"), SecretDeliveryResponse.class))
        .isEqualTo(secretDeliveryResponse);
    assertThat(fromJson(asJson(secretDeliveryResponse), SecretDeliveryResponse.class)).isEqualTo(
        secretDeliveryResponse);

    SecretDeliveryResponse secretDeliveryResponseWithVersion = new SecretDeliveryResponse(
        "General_Password",
        secret,
        decodedLength(secret),
        "general_checksum",
        ApiDate.parse("2011-09-29T15:46:00.000Z"),
        ApiDate.parse("2011-09-29T15:46:00.000Z"),
        null);
    assertThat(
        fromJson(jsonFixture("fixtures/secretDeliveryResponseWithVersion.json"),
            SecretDeliveryResponse.class))
        .isEqualTo(secretDeliveryResponseWithVersion);
    assertThat(fromJson(asJson(secretDeliveryResponseWithVersion),
        SecretDeliveryResponse.class)).isEqualTo(secretDeliveryResponseWithVersion);
  }

  // The keys in the metadata field are removed from that field and serialized along
  // with the rest of the fields, so a SecretDeliveryResponse that includes metadata
  // cannot be serialized and deserialized successfully.
  @Test public void unpacksMetadata() {
    String secret = "YXNkZGFz";

    SecretDeliveryResponse secretDeliveryResponseWithMetadata = new SecretDeliveryResponse(
        "Nobody_PgPass",
        secret,
        decodedLength(secret),
        "nobody_checksum",
        ApiDate.parse("2011-09-29T15:46:00.000Z"),
        ApiDate.parse("2011-10-29T15:46:00.000Z"),
        ImmutableMap.of("mode", "0400", "owner", "nobody"));
    assertThatThrownBy(() ->
        fromJson(jsonFixture("fixtures/secretDeliveryResponseWithMetadata.json"),
            SecretDeliveryResponse.class))
        .isInstanceOf(UnrecognizedPropertyException.class)
        .hasMessageContaining("Unrecognized field \"mode\"");

    assertThatThrownBy(() -> fromJson(asJson(secretDeliveryResponseWithMetadata),
        SecretDeliveryResponse.class))
        .isInstanceOf(UnrecognizedPropertyException.class)
        .hasMessageContaining("Unrecognized field \"mode\"");
  }
}
