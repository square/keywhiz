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
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class CreateGroupRequestV2Test {
  private CreateGroupRequestV2 createGroupRequest = CreateGroupRequestV2.builder()
      .name("group-name")
      .description("group-description")
      .metadata(ImmutableMap.of("app", "group-app"))
      .owner("owner")
      .build();

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(createGroupRequest), CreateGroupRequestV2.class)).isEqualTo(
        createGroupRequest);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(
        jsonFixture("fixtures/v2/createGroupRequest.json"), CreateGroupRequestV2.class))
        .isEqualTo(createGroupRequest);
  }

  @Test public void deserializesOriginalVersion() throws Exception {
    CreateGroupRequestV2 originalVersion = CreateGroupRequestV2.builder()
        .name("group-name")
        .description("group-description")
        .metadata(ImmutableMap.of("app", "group-app"))
        .build();

    assertThat(fromJson(
        jsonFixture("fixtures/v2/createGroupRequestOriginalVersion.json"), CreateGroupRequestV2.class))
        .isEqualTo(originalVersion);
  }

  @Test(expected = IllegalStateException.class)
  public void emptyNameFailsValidation() {
    CreateGroupRequestV2.builder()
        .name("")
        .build();
  }

  @Test public void builderAllowsNullOwner() {
    CreateGroupRequestV2.builder()
        .name("name")
        .owner(null)
        .build();
  }
}
