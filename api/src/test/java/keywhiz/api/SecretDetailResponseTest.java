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
import keywhiz.api.model.Group;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretDetailResponseTest {
  private SecretDetailResponse secretDetailResponse = new SecretDetailResponse(
      1000,
      "secretName",
      "desc",
      ApiDate.parse("2013-03-28T21:23:00.000Z"),
      "keywhizAdmin",
      ApiDate.parse("2013-03-28T21:23:04.159Z"),
      "keywhizAdmin",
      ImmutableMap.of("mode", "0660"),
      ImmutableList.of(
          new Group(2000,
              "someGroup",
              "groupDesc",
              ApiDate.parse("2013-03-28T21:29:27.465Z"),
              "keywhizAdmin",
              ApiDate.parse("2013-03-28T21:29:27.465Z"),
              "keywhizAdmin",
              ImmutableMap.of("app", "keywhiz"))
      ),
      ImmutableList.of());

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(secretDetailResponse), SecretDetailResponse.class)).isEqualTo(
        secretDetailResponse);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(jsonFixture("fixtures/secretDetailResponse.json"),
        SecretDetailResponse.class)).isEqualTo(secretDetailResponse);
  }
}
