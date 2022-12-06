package keywhiz.api.automation.v2;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class CreateOrUpdateSecretInfoV2Test {
  @Test
  public void roundTripSerialization() {
    CreateOrUpdateSecretInfoV2 info = createInfo();
    assertThat(fromJson(asJson(info), CreateOrUpdateSecretInfoV2.class)).isEqualTo(info);
  }

  @Test
  public void deserializesCorrectly() {
    assertThat(fromJson(jsonFixture("fixtures/v2/createOrUpdateSecretInfo.json"),
        CreateOrUpdateSecretInfoV2.class)).isEqualTo(createInfo());
  }

  @Test(expected = IllegalStateException.class)
  public void nameIsRequired() {
    CreateOrUpdateSecretInfoV2.builder()
        .content(content())
        .build();
  }

  @Test
  public void mvp() {
    CreateOrUpdateSecretInfoV2.builder()
        .content(content())
        .name("name")
        .build();
  }

  private static CreateOrUpdateSecretInfoV2 createInfo() {
    return CreateOrUpdateSecretInfoV2.builder()
        .content(content())
        .description("description")
        .expiry(1234)
        .metadata(ImmutableMap.of("foo", "bar"))
        .name("name")
        .owner("owner")
        .type("type")
        .build();
  }

  private static String content() {
    return BaseEncoding.base64().encode("content".getBytes(StandardCharsets.UTF_8));
  }
}
