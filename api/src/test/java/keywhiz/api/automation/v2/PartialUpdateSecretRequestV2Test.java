package keywhiz.api.automation.v2;

import org.junit.Test;

public class PartialUpdateSecretRequestV2Test {
  @Test
  public void builderAllowsNullOwner() {
    PartialUpdateSecretRequestV2.builder()
        .ownerPresent(true)
        .owner(null)
        .build();
  }
}
