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
import java.time.OffsetDateTime;
import java.util.Base64;
import keywhiz.api.model.Secret;
import keywhiz.api.model.VersionGenerator;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static keywhiz.api.SecretDeliveryResponse.decodedLength;
import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class SecretDeliveryResponseTest {
  private static final Base64.Encoder BASE64 = Base64.getEncoder();

  private static final ImmutableMap<String, String> metadata =
      ImmutableMap.of("key1", "value1", "key2", "value2");
  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final Secret secret = new Secret(0, "name", VersionGenerator.now().toHex(), null,
      "YWJj", NOW, null, NOW, null, metadata, "upload", null);

  @Test
  public void setsLength() {
    SecretDeliveryResponse response = SecretDeliveryResponse.fromSecret(secret);
    assertThat(response.getSecretLength()).isEqualTo(decodedLength("YWJj")).isEqualTo(3);
  }

  @Test
  public void hasVersionedName() {
    SecretDeliveryResponse response = SecretDeliveryResponse.fromSecret(secret);
    assertThat(response.getName()).matches("name" + Secret.VERSION_DELIMITER + "[0-9a-f]+");
  }

  @Test
  public void hasMetaData() {
    SecretDeliveryResponse response = SecretDeliveryResponse.fromSecret(secret);
    assertThat(response.getMetadata()).contains(entry("key2", "value2"), entry("key1", "value1"));
  }

  @Test
  public void decodedLengthCalculates() {
    String base64 = BASE64.encodeToString("31337".getBytes(UTF_8));
    assertThat(decodedLength(base64)).isEqualTo(5);

    base64 = BASE64.withoutPadding().encodeToString("31337".getBytes(UTF_8));
    assertThat(decodedLength(base64)).isEqualTo(5);

    String longerBase64 = BASE64.encodeToString("313373133731337".getBytes(UTF_8));
    assertThat(decodedLength(longerBase64)).isEqualTo(15);

    longerBase64 = BASE64.withoutPadding().encodeToString("313373133731337".getBytes(UTF_8));
    assertThat(decodedLength(longerBase64)).isEqualTo(15);

    assertThat(decodedLength("")).isZero();
  }

  @Test
  public void serializesCorrectly() throws Exception {
    String secret = "YXNkZGFz";

    SecretDeliveryResponse secretDeliveryResponse = new SecretDeliveryResponse(
        "Database_Password",
        secret,
        decodedLength(secret),
        OffsetDateTime.parse("2011-09-29T15:46:00.232Z"),
        false,
        ImmutableMap.of());
    assertThat(asJson(secretDeliveryResponse))
        .isEqualTo(jsonFixture("fixtures/secretDeliveryResponse.json"));

    SecretDeliveryResponse secretDeliveryResponseWithVersion = new SecretDeliveryResponse(
        "General_Password..0be68f903f8b7d86",
        secret,
        decodedLength(secret),
        OffsetDateTime.parse("2011-09-29T15:46:00.312Z"),
        true,
        ImmutableMap.of());
    assertThat(asJson(secretDeliveryResponseWithVersion))
        .isEqualTo(jsonFixture("fixtures/secretDeliveryResponseWithVersion.json"));

    SecretDeliveryResponse secretDeliveryResponseWithMetadata = new SecretDeliveryResponse(
        "Nobody_PgPass",
        secret,
        decodedLength(secret),
        OffsetDateTime.parse("2011-09-29T15:46:00.232Z"),
        false,
        ImmutableMap.of("mode", "0400", "owner", "nobody"));
    assertThat(asJson(secretDeliveryResponseWithMetadata))
        .isEqualTo(jsonFixture("fixtures/secretDeliveryResponseWithMetadata.json"));
  }
}
