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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import keywhiz.api.model.SanitizedSecret;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretsResponseTest {
  SecretsResponse secretsResponse = new SecretsResponse(ImmutableList.of(
      SanitizedSecret.of(
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
          ImmutableMap.of("param1", "value1"),
          1136214245,
          1L,
          ApiDate.parse("2013-03-28T21:42:42.573Z"),
          "keywhizAdmin"),
      SanitizedSecret.of(
          768,
          "anotherSecret",
          "",
          "checksum",
          ApiDate.parse("2013-04-28T21:42:42.573Z"),
          "keywhizAdmin",
          ApiDate.parse("2013-04-28T21:42:42.573Z"),
          "keywhizAdmin",
          null,
          "upload",
          null,
          1136214245,
          10L,
          ApiDate.parse("2013-04-28T21:42:42.573Z"),
          "keywhizAdmin")
  ));

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(secretsResponse), SecretsResponse.class)).isEqualTo(secretsResponse);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(
        fromJson(jsonFixture("fixtures/secretsResponse.json"), SecretsResponse.class)).isEqualTo(
        secretsResponse);
  }
}
