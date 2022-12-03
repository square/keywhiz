package keywhiz.api.automation.v2;

import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class BatchCreateOrUpdateSecretsRequestV2Test {
  @Test
  public void roundTripSerialization() {
    BatchCreateOrUpdateSecretRequestV2 request = createRequest();
    assertThat(fromJson(asJson(request), BatchCreateOrUpdateSecretRequestV2.class)).isEqualTo(request);
  }

  @Test
  public void deserializesCorrectly() {
    assertThat(fromJson(jsonFixture("fixtures/v2/batchCreateOrUpdateSecretRequest.json"),
        BatchCreateOrUpdateSecretRequestV2.class)).isEqualTo(createRequest());
  }

  private static BatchCreateOrUpdateSecretRequestV2 createRequest() {
    CreateOrUpdateSecretInfoV2 secret = CreateOrUpdateSecretInfoV2.builder()
        .content(BaseEncoding.base64().encode("content".getBytes(StandardCharsets.UTF_8)))
        .name("name")
        .build();

    return BatchCreateOrUpdateSecretRequestV2.builder()
        .batchMode(BatchMode.BEST_EFFORT)
        .secrets(secret)
        .build();
  }
}
