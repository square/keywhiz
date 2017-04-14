package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

@AutoValue public abstract class SecretContentsResponseV2 {
  SecretContentsResponseV2() {} // prevent sub-classing


  public static Builder builder() {
    return new AutoValue_SecretContentsResponseV2.Builder()
        .successSecrets(ImmutableMap.of())
        .missingSecrets(ImmutableList.of());
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract SecretContentsResponseV2.Builder successSecrets(ImmutableMap<String, String> successSecrets);
    abstract SecretContentsResponseV2.Builder missingSecrets(ImmutableList<String> missingSecrets);


    public SecretContentsResponseV2.Builder successSecrets(Map<String, String> successSecrets) {
      return successSecrets(ImmutableMap.copyOf(successSecrets));
    }
    public SecretContentsResponseV2.Builder missingSecrets(List<String> missingSecrets) {
      return missingSecrets(ImmutableList.copyOf(missingSecrets));
    }

    public abstract SecretContentsResponseV2 build();
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static SecretContentsResponseV2 fromParts(
      @JsonProperty("successSecrets") ImmutableMap<String, String> successSecrets,
      @JsonProperty("missingSecrets") ImmutableList<String> missingSecrets) {
    return builder().successSecrets(successSecrets).missingSecrets(missingSecrets).build();
  }

  @JsonProperty("successSecrets") public abstract ImmutableMap<String, String> successSecrets();
  @JsonProperty("missingSecrets") public abstract ImmutableList<String> missingSecrets();
}
