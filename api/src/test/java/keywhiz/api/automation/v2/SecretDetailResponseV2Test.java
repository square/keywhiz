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
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretDetailResponseV2Test {
  @Test public void serializesCorrectly() throws Exception {
    SecretDetailResponseV2 secretDetailResponse = SecretDetailResponseV2.builder()
        .name("secret-name")
        .description("secret-description")
        .content("YXNkZGFz")
        .createdAtSeconds(OffsetDateTime.parse("2013-03-28T21:23:04.159Z").toEpochSecond())
        .createdBy("creator-user")
        .type("text/plain")
        .metadata(ImmutableMap.of("owner", "root"))
        .expiry(1136214245)
        .build();

    assertThat(asJson(secretDetailResponse))
        .isEqualTo(jsonFixture("fixtures/v2/secretDetailResponse.json"));
  }
}
