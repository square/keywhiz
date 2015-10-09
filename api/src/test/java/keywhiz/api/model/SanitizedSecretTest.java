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
import java.time.OffsetDateTime;

import keywhiz.api.ApiDate;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SanitizedSecretTest {
  @Test public void serializesCorrectly() throws Exception {
    SanitizedSecret sanitizedSecret = SanitizedSecret.of(
      767,
      "trapdoor",
      "0be6a2fc049ee349",
      "v1",
      ApiDate.parse("2013-03-28T21:42:42.573Z"),
      "keywhizAdmin",
      ApiDate.parse("2013-03-28T21:42:42.573Z"),
      "keywhizAdmin",
      ImmutableMap.of("owner", "the king"),
      "password",
      ImmutableMap.of("favoriteFood", "PB&J sandwich"));

    assertThat(asJson(sanitizedSecret))
        .isEqualTo(jsonFixture("fixtures/sanitizedSecret.json"));
  }
}
