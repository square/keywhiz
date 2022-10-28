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

public class GroupTest {
  private static final String NO_OWNER = null;

  private Group group = new Group(330,
      "someGroup",
      "groupDesc",
      ApiDate.parse("2013-03-28T21:29:27.465Z"),
      "keywhizAdmin",
      ApiDate.parse("2013-03-28T21:29:27.465Z"),
      "keywhizAdmin",
      ImmutableMap.of("app", "keywhiz"),
      "owner");

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(group), Group.class)).isEqualTo(group);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(jsonFixture("fixtures/model/group.json"), Group.class)).isEqualTo(group);
  }

  @Test public void deserializesOriginalVersion() throws Exception {
    Group originalVersion = new Group(330,
        "someGroup",
        "groupDesc",
        ApiDate.parse("2013-03-28T21:29:27.465Z"),
        "keywhizAdmin",
        ApiDate.parse("2013-03-28T21:29:27.465Z"),
        "keywhizAdmin",
        ImmutableMap.of("app", "keywhiz"),
        NO_OWNER);
    assertThat(fromJson(jsonFixture("fixtures/model/groupOriginalVersion.json"), Group.class)).isEqualTo(originalVersion);
  }
}
