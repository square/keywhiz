package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

@AutoValue public abstract class SecretContentsAtVersionResponseV2 {
  SecretContentsAtVersionResponseV2() {} // prevent sub-classing


  public static Builder builder() {
    return new AutoValue_SecretContentsAtVersionResponseV2.Builder().secret("");
  }

  @AutoValue.Builder public abstract static class Builder {
    public abstract SecretContentsAtVersionResponseV2.Builder secret(String secret);
    public abstract SecretContentsAtVersionResponseV2 build();
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static SecretContentsAtVersionResponseV2 fromParts(
      @JsonProperty("secret") String secret) {
    return builder().secret(secret).build();
  }

  @JsonProperty("secret") public abstract String secret();
}
