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
import java.util.ArrayList;
import java.util.List;
import keywhiz.api.ApiDate;
import org.junit.Before;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SanitizedSecretWithGroupsTest {
  private SanitizedSecret sanitizedSecret = SanitizedSecret.of(
      767,
      "trapdoor",
      "v1",
      "checksum",
      ApiDate.parse("2013-03-28T21:42:42.000Z"),
      "keywhizAdmin",
      ApiDate.parse("2013-03-28T21:42:42.000Z"),
      "keywhizAdmin",
      ImmutableMap.of("owner", "the king"),
      "password",
      ImmutableMap.of("favoriteFood", "PB&J sandwich"),
      1136214245,
      1L);

  private List<Group> groups;

  @Before
  public void setUp() {
    groups = new ArrayList<>();
    groups.add(new Group(100, "group1", "test-group-1", ApiDate.parse("2013-03-28T21:42:42.000Z"),
        "keywhizAdmin",
        ApiDate.parse("2013-03-28T21:42:42.000Z"),
        "keywhizAdmin",
        ImmutableMap.of("owner", "the king")));
    groups.add(new Group(100, "group2", "test-group-1", ApiDate.parse("2013-03-28T21:42:42.000Z"),
        null,
        ApiDate.parse("2013-03-28T21:42:42.000Z"), null, null));
  }

  @Test public void serializesCorrectly() throws Exception {
    SanitizedSecretWithGroups sanitizedSecretWithGroups = SanitizedSecretWithGroups.of(sanitizedSecret, groups);
    assertThat(asJson(sanitizedSecretWithGroups))
        .isEqualTo(jsonFixture("fixtures/sanitizedSecretWithGroups.json"));
  }

  @Test public void buildsCorrectlyFromSecretAndGroups() throws Exception {
    SanitizedSecretWithGroups sanitizedSecretWithGroups = SanitizedSecretWithGroups.fromSecret(
        new Secret(
            767,
            "trapdoor",
            "v1",
            () -> "foo",
            "checksum",
            ApiDate.parse("2013-03-28T21:42:42.000Z"),
            "keywhizAdmin",
            ApiDate.parse("2013-03-28T21:42:42.000Z"),
            "keywhizAdmin",
            ImmutableMap.of("owner", "the king"),
            "password",
            ImmutableMap.of("favoriteFood", "PB&J sandwich"),
            1136214245,
            1L), groups);

    assertThat(asJson(sanitizedSecretWithGroups))
        .isEqualTo(jsonFixture("fixtures/sanitizedSecretWithGroups.json"));
  }

  @Test public void buildsCorrectlyFromSecretSeriesAndContent() throws Exception {
    SanitizedSecretWithGroups sanitizedSecretWithGroups = SanitizedSecretWithGroups.fromSecretSeriesAndContentAndGroups(
        SecretSeriesAndContent.of(
            SecretSeries.of(
                767,
                "trapdoor",
                "v1",
                ApiDate.parse("2013-03-28T21:42:42.000Z"),
                "keywhizAdmin",
                ApiDate.parse("2013-03-28T21:42:42.000Z"),
                "keywhizAdmin",
                "password",
                ImmutableMap.of("favoriteFood", "PB&J sandwich"),
                1L
            ), SecretContent.of(
                1L,
                767,
                "foo",
                "checksum",
                ApiDate.parse("2013-03-28T21:42:42.000Z"),
                "keywhizAdmin",
                ApiDate.parse("2013-03-28T21:42:42.000Z"),
                "keywhizAdmin",
                ImmutableMap.of("owner", "the king"),
                1136214245L
            )), groups);

    assertThat(asJson(sanitizedSecretWithGroups))
        .isEqualTo(jsonFixture("fixtures/sanitizedSecretWithGroups.json"));
  }
}
