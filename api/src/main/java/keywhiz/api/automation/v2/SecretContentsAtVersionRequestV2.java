package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue public abstract class SecretContentsAtVersionRequestV2 {
  SecretContentsAtVersionRequestV2() {} // prevent sub-classing


  public static Builder builder() {
    return new AutoValue_SecretContentsAtVersionRequestV2.Builder()
        .secret("")
        .version(0L);
  }

  @AutoValue.Builder public abstract static class Builder {
    public abstract SecretContentsAtVersionRequestV2.Builder secret(String secrets);
    public abstract SecretContentsAtVersionRequestV2.Builder version(Long version);

    public abstract SecretContentsAtVersionRequestV2 build();
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static SecretContentsAtVersionRequestV2 fromParts(
      @JsonProperty("secret") String secret,
      @JsonProperty("version") Long version
  ) {
    return builder()
            .secret(secret)
            .version(version)
            .build();
  }

  @JsonProperty("secret") public abstract String secret();
  @JsonProperty("version") public abstract Long version();
}
