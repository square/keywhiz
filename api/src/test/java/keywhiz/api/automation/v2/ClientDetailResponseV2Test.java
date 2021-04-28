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

import java.time.OffsetDateTime;
import java.util.Optional;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class ClientDetailResponseV2Test {
  private ClientDetailResponseV2 clientDetailResponse = new AutoValue_ClientDetailResponseV2(
      "Client Name",
      "Client Description",
      "spiffe//example.org/client-name",
      OffsetDateTime.parse("2012-08-01T13:15:30Z").toEpochSecond(),
      OffsetDateTime.parse("2012-09-10T03:15:30Z").toEpochSecond(),
      "creator-user",
      "updater-user",
      Optional.of(OffsetDateTime.parse("2012-09-10T03:15:30Z").toEpochSecond())
  );

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(clientDetailResponse), ClientDetailResponseV2.class)).isEqualTo(
        clientDetailResponse);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(jsonFixture("fixtures/v2/clientDetailResponse.json"),
        ClientDetailResponseV2.class)).isEqualTo(clientDetailResponse);
  }

  @Test public void deserializesNullLastSeenCorrectly() throws Exception {
    ApiDate createdAt = new ApiDate(1343826930);
    ApiDate updatedAt = new ApiDate(1347246930);

    Client client = new Client(0, "Client Name", "Client Description", null, createdAt,
        "creator-user", updatedAt, "updater-user", null, null, true, false
    );
    ClientDetailResponseV2 clientDetailResponse = ClientDetailResponseV2.fromClient(client);

    assertThat(fromJson(jsonFixture("fixtures/v2/clientDetailResponse_LastSeenNull.json"),
        ClientDetailResponseV2.class)).isEqualTo(clientDetailResponse);
    assertThat(fromJson(asJson(clientDetailResponse), ClientDetailResponseV2.class)).isEqualTo(
        clientDetailResponse);
  }
}
