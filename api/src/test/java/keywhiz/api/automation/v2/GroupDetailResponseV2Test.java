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
import com.google.common.collect.ImmutableSet;
import java.time.OffsetDateTime;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class GroupDetailResponseV2Test {
  @Test public void serializesCorrectly() throws Exception {
    GroupDetailResponseV2 groupDetailResponse = GroupDetailResponseV2.builder()
        .name("group-name")
        .description("group-description")
        .createdAtSeconds(OffsetDateTime.parse("2012-08-01T13:15:30Z").toEpochSecond())
        .updatedAtSeconds(OffsetDateTime.parse("2012-09-10T03:15:30Z").toEpochSecond())
        .createdBy("creator-user")
        .updatedBy("updater-user")
        .secrets(ImmutableSet.of("secret1", "secret2"))
        .clients(ImmutableSet.of("client1", "client2"))
        .metadata(ImmutableMap.of("app", "keywhiz"))
        .build();

    assertThat(asJson(groupDetailResponse))
        .isEqualTo(jsonFixture("fixtures/v2/groupDetailResponse.json"));
  }
}
