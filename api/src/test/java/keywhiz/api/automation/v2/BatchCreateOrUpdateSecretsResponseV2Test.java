package keywhiz.api.automation.v2;

import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class BatchCreateOrUpdateSecretsResponseV2Test {
  @Test
  public void roundTripSerialization() {
    BatchCreateOrUpdateSecretsResponseV2 response = createResponse();
    assertThat(fromJson(asJson(response), BatchCreateOrUpdateSecretsResponseV2.class)).isEqualTo(response);
  }

  @Test
  public void deserializesCorrectly() {
    assertThat(fromJson(jsonFixture("fixtures/v2/batchCreateOrUpdateSecretsResponse.json"),
        BatchCreateOrUpdateSecretsResponseV2.class)).isEqualTo(createResponse());
  }

  private static BatchCreateOrUpdateSecretsResponseV2 createResponse() {
    return BatchCreateOrUpdateSecretsResponseV2.builder()
        .build();
  }
}
