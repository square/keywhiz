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

import org.junit.Test;

import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretContentsRequestV2Test {
  @Test public void deserializesCorrectly() throws Exception {
    SecretContentsRequestV2 secretContentsRequest = SecretContentsRequestV2.builder()
        .secrets("secret1", "secret2", "secret3")
        .build();

    assertThat(fromJson(
        jsonFixture("fixtures/v2/secretContentsRequest.json"), SecretContentsRequestV2.class))
        .isEqualTo(secretContentsRequest);
  }
}
