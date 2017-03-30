package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

@AutoValue public abstract class SecretContentsRequestV2 {
  SecretContentsRequestV2() {} // prevent sub-classing


  public static Builder builder() {
    return new AutoValue_SecretContentsRequestV2.Builder()
        .secrets();
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract SecretContentsRequestV2.Builder secrets(ImmutableSet<String> secrets);

    public SecretContentsRequestV2.Builder secrets(String... secrets) {
      return secrets(ImmutableSet.copyOf(secrets));
    }

    public abstract SecretContentsRequestV2 build();
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static SecretContentsRequestV2 fromParts(
      @JsonProperty("secrets") ImmutableSet<String> secrets) {
    return builder().secrets(secrets).build();
  }

  @JsonProperty("secrets") public abstract ImmutableSet<String> secrets();
}
