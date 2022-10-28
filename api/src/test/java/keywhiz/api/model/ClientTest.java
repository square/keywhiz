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

import keywhiz.api.ApiDate;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class ClientTest {
  private static final String NO_OWNER = null;

  private Client client = new Client(200,
      "someClient",
      "clientDesc",
      null,
      ApiDate.parse("2013-03-28T21:29:27.465Z"),
      "keywhizAdmin",
      ApiDate.parse("2013-03-28T21:29:27.465Z"),
      "keywhizAdmin",
      null,
      null,
      true,
      "owner",
      false
  );

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(client), Client.class)).isEqualTo(client);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(jsonFixture("fixtures/model/client.json"), Client.class)).isEqualTo(client);
  }

  @Test public void deserializesOriginalVersion() throws Exception {
    Client originalVersion = new Client(200,
        "someClient",
        "clientDesc",
        null,
        ApiDate.parse("2013-03-28T21:29:27.465Z"),
        "keywhizAdmin",
        ApiDate.parse("2013-03-28T21:29:27.465Z"),
        "keywhizAdmin",
        null,
        null,
        true,
        NO_OWNER,
        false
    );

    assertThat(
        fromJson(jsonFixture("fixtures/model/clientOriginalVersion.json"), Client.class))
        .isEqualTo(originalVersion);
  }
}
