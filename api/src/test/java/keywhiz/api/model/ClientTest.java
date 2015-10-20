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

import java.time.OffsetDateTime;

import keywhiz.api.ApiDate;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class ClientTest {
  @Test public void serializesCorrectly() throws Exception {
    Client client = new Client(200,
                               "someClient",
                               "clientDesc",
                               ApiDate.parse("2013-03-28T21:29:27.465Z"),
                               "keywhizAdmin",
                               ApiDate.parse("2013-03-28T21:29:27.465Z"),
                               "keywhizAdmin",
                               true,
                               false);

    assertThat(asJson(client)).isEqualTo(jsonFixture("fixtures/client.json"));
  }
}
