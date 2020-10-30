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

package keywhiz.api.model;

import com.google.common.collect.ImmutableMap;
import keywhiz.api.ApiDate;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SanitizedSecretTest {
  private SanitizedSecret sanitizedSecret = SanitizedSecret.of(
      767,
      "trapdoor",
      "v1",
      "checksum",
      ApiDate.parse("2013-03-28T21:42:42.573Z"),
      "keywhizAdmin",
      ApiDate.parse("2013-03-28T21:42:42.573Z"),
      "keywhizAdmin",
      ImmutableMap.of("owner", "the king"),
      "password",
      ImmutableMap.of("favoriteFood", "PB&J sandwich"),
      1136214245,
      1L,
      ApiDate.parse("2013-03-28T21:42:42.573Z"),
      "keywhizAdmin");

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(sanitizedSecret), SanitizedSecret.class)).isEqualTo(sanitizedSecret);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(jsonFixture("fixtures/sanitizedSecret.json"), SanitizedSecret.class))
        .isEqualTo(sanitizedSecret);
  }

  @Test public void buildsCorrectlyFromSecret() {
    SanitizedSecret fromSecret = SanitizedSecret.fromSecret(
        new Secret(
            767,
            "trapdoor",
            "v1",
            () -> "foo",
            "checksum",
            ApiDate.parse("2013-03-28T21:42:42.573Z"),
            "keywhizAdmin",
            ApiDate.parse("2013-03-28T21:42:42.573Z"),
            "keywhizAdmin",
            ImmutableMap.of("owner", "the king"),
            "password",
            ImmutableMap.of("favoriteFood", "PB&J sandwich"),
            1136214245,
            1L,
            ApiDate.parse("2013-03-28T21:42:42.573Z"),
            "keywhizAdmin"));

    assertThat(fromSecret).isEqualTo(sanitizedSecret);
  }

  @Test public void buildsCorrectlyFromSecretSeriesAndContent() {
    SanitizedSecret fromSecretSeriesAndContent = SanitizedSecret.fromSecretSeriesAndContent(
        SecretSeriesAndContent.of(
            SecretSeries.of(
                767,
                "trapdoor",
                "v1",
                ApiDate.parse("2013-03-28T21:42:42.573Z"),
                "keywhizAdmin",
                ApiDate.parse("2013-03-28T21:42:42.573Z"),
                "keywhizAdmin",
                "password",
                ImmutableMap.of("favoriteFood", "PB&J sandwich"),
                1L
            ), SecretContent.of(
                1L,
                767,
                "foo",
                "checksum",
                ApiDate.parse("2013-03-28T21:42:42.573Z"),
                "keywhizAdmin",
                ApiDate.parse("2013-03-28T21:42:42.573Z"),
                "keywhizAdmin",
                ImmutableMap.of("owner", "the king"),
                1136214245L
            )));

    assertThat(fromSecretSeriesAndContent).isEqualTo(sanitizedSecret);
  }

  // Make sure that a SanitizedSecret can be correctly created from a JSON
  // representation which does not include the newer ___Seconds fields
  @Test public void buildsCorrectlyFromLegacyJson() throws Exception {
    String legacyJson = jsonFixture("fixtures/sanitizedSecretWithoutSeconds.json");
    SanitizedSecret legacySecret = fromJson(legacyJson, SanitizedSecret.class);

    String newJson = jsonFixture("fixtures/sanitizedSecret.json");
    SanitizedSecret newSecret = fromJson(newJson, SanitizedSecret.class);

    assertThat(legacySecret).isEqualTo(newSecret);
  }
}
