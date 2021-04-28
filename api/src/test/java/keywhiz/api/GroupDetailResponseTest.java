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
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class GroupDetailResponseTest {
  private GroupDetailResponse groupDetailResponse = new GroupDetailResponse(
      234,
      "group-name",
      "",
      ApiDate.parse("2012-08-01T13:15:30.000Z"),
      ApiDate.parse("2012-09-10T03:15:30.000Z"),
      "creator-user",
      "updater-user",
      ImmutableMap.of("app", "keywhiz"),
      ImmutableList.of(),
      ImmutableList.of());

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(groupDetailResponse), GroupDetailResponse.class)).isEqualTo(
        groupDetailResponse);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(jsonFixture("fixtures/groupDetailResponse.json"),
        GroupDetailResponse.class)).isEqualTo(groupDetailResponse);
  }
}
