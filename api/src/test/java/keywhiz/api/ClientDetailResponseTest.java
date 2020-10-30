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
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class ClientDetailResponseTest {
  private ClientDetailResponse clientDetailResponse = new ClientDetailResponse(
      9875,
      "Client Name",
      "Client Description",
      "spiffe//example.org/client-name",
      ApiDate.parse("2012-08-01T13:15:30.001Z"),
      ApiDate.parse("2012-09-10T03:15:30.001Z"),
      "creator-user",
      "updater-user",
      ApiDate.parse("2012-09-10T03:15:30.001Z"),
      ImmutableList.of(),
      ImmutableList.of());

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(clientDetailResponse), ClientDetailResponse.class)).isEqualTo(
        clientDetailResponse);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(jsonFixture("fixtures/clientDetailResponse.json"),
        ClientDetailResponse.class)).isEqualTo(clientDetailResponse);
  }

  @Test public void handlesNullLastSeenCorrectly() throws Exception {
    ClientDetailResponse clientDetailResponse = new ClientDetailResponse(
        9875,
        "Client Name",
        "Client Description",
        "spiffe//example.org/client-name",
        ApiDate.parse("2012-08-01T13:15:30.001Z"),
        ApiDate.parse("2012-09-10T03:15:30.001Z"),
        "creator-user",
        "updater-user",
        null,
        ImmutableList.of(),
        ImmutableList.of());

    assertThat(fromJson(jsonFixture("fixtures/clientDetailResponse_NullLastSeen.json"),
        ClientDetailResponse.class)).isEqualTo(clientDetailResponse);

    assertThat(fromJson(asJson(clientDetailResponse), ClientDetailResponse.class)).isEqualTo(
        clientDetailResponse);
  }
}
