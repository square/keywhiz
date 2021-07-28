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

package keywhiz.api.automation.v2;

import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretDetailResponseV2Test {
  private SecretDetailResponseV2 secretDetailResponse = SecretDetailResponseV2.builder()
      .name("secret-name")
      .owner("secret-owner")
      .version(1L)
      .description("secret-description")
      .checksum("checksum")
      .createdAtSeconds(OffsetDateTime.parse("2013-03-28T21:23:04.159Z").toEpochSecond())
      .createdBy("creator-user")
      .updatedAtSeconds(OffsetDateTime.parse("2014-03-28T21:23:04.159Z").toEpochSecond())
      .updatedBy("updater-user")
      .type("text/plain")
      .metadata(ImmutableMap.of("owner", "root"))
      .expiry(1136214245)
      .contentCreatedAtSeconds(OffsetDateTime.parse("2014-03-28T21:23:04.159Z").toEpochSecond())
      .contentCreatedBy("updater-user")
      .build();

  @Test
  public void builderAllowsNullOwner() {
    SecretDetailResponseV2.builder().owner(null);
  }

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(secretDetailResponse), SecretDetailResponseV2.class)).isEqualTo(
        secretDetailResponse);
  }

  @Test public void responseWithoutOwnerDeserializesCorrectly() throws Exception {
    SecretDetailResponseV2 response = SecretDetailResponseV2.builder()
        .name("secret-name")
        .version(1L)
        .description("secret-description")
        .checksum("checksum")
        .createdAtSeconds(OffsetDateTime.parse("2013-03-28T21:23:04.159Z").toEpochSecond())
        .createdBy("creator-user")
        .updatedAtSeconds(OffsetDateTime.parse("2014-03-28T21:23:04.159Z").toEpochSecond())
        .updatedBy("updater-user")
        .type("text/plain")
        .metadata(ImmutableMap.of("owner", "root"))
        .expiry(1136214245)
        .contentCreatedAtSeconds(OffsetDateTime.parse("2014-03-28T21:23:04.159Z").toEpochSecond())
        .contentCreatedBy("updater-user")
        .build();

    assertThat(fromJson(jsonFixture("fixtures/v2/secretDetailResponse.json"),
        SecretDetailResponseV2.class)).isEqualTo(response);
  }

  @Test public void responseWithOwnerDeserializesCorrectly() throws Exception {
    SecretDetailResponseV2 response = SecretDetailResponseV2.builder()
        .name("secret-name")
        .owner("secret-owner")
        .version(1L)
        .description("secret-description")
        .checksum("checksum")
        .createdAtSeconds(OffsetDateTime.parse("2013-03-28T21:23:04.159Z").toEpochSecond())
        .createdBy("creator-user")
        .updatedAtSeconds(OffsetDateTime.parse("2014-03-28T21:23:04.159Z").toEpochSecond())
        .updatedBy("updater-user")
        .type("text/plain")
        .metadata(ImmutableMap.of("owner", "root"))
        .expiry(1136214245)
        .contentCreatedAtSeconds(OffsetDateTime.parse("2014-03-28T21:23:04.159Z").toEpochSecond())
        .contentCreatedBy("updater-user")
        .build();

    assertThat(fromJson(jsonFixture("fixtures/v2/secretDetailResponseWithOwner.json"),
        SecretDetailResponseV2.class)).isEqualTo(response);
  }

  @Test public void formsCorrectlyFromSecretSeriesAndContent() {
    SecretSeries series = SecretSeries.of(
        1,
        "secret-name",
        "secret-owner",
        "secret-description",
        ApiDate.parse("2013-03-28T21:23:04.159Z"),
        "creator-user",
        ApiDate.parse("2014-03-28T21:23:04.159Z"),
        "updater-user",
        "text/plain",
        null,
        1L);
    SecretContent content = SecretContent.of(
        1,
        1,
        "YXNkZGFz",
        "checksum",
        ApiDate.parse("2014-03-28T21:23:04.159Z"),
        "updater-user",
        ApiDate.parse("2014-03-28T21:23:04.159Z"),
        "updater-user",
        ImmutableMap.of("owner", "root"),
        1136214245);
    SecretSeriesAndContent seriesAndContent = SecretSeriesAndContent.of(series, content);
    SecretDetailResponseV2 fromSeriesAndContent = SecretDetailResponseV2.builder()
        .seriesAndContent(seriesAndContent)
        .build();

    assertThat(fromSeriesAndContent).isEqualTo(secretDetailResponse);
  }

  @Test public void formsCorrectlyFromSanitizedSecret() {
    SanitizedSecret sanitizedSecret =
        SanitizedSecret.of(
            1,
            "secret-name",
            "secret-owner",
            "secret-description",
            "checksum",
            ApiDate.parse("2013-03-28T21:23:04.159Z"),
            "creator-user",
            ApiDate.parse("2014-03-28T21:23:04.159Z"),
            "updater-user",
            ImmutableMap.of("owner", "root"),
            "text/plain", null,
            1136214245,
            1L,
            ApiDate.parse("2014-03-28T21:23:04.159Z"),
            "updater-user");
    SecretDetailResponseV2 fromSanitizedSecret = SecretDetailResponseV2.builder()
        .sanitizedSecret(sanitizedSecret)
        .build();

    assertThat(fromSanitizedSecret).isEqualTo(secretDetailResponse);
  }
}
