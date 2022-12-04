package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.util.List;

@AutoValue public abstract class BatchCreateOrUpdateSecretsRequestV2 {
  BatchCreateOrUpdateSecretsRequestV2() {}

  public static BatchCreateOrUpdateSecretsRequestV2.Builder builder() {
    return new AutoValue_BatchCreateOrUpdateSecretsRequestV2.Builder();
  }

  @AutoValue.Builder public abstract static class Builder {
    abstract Builder secrets(List<CreateOrUpdateSecretInfoV2> secrets);

    public Builder secrets(CreateOrUpdateSecretInfoV2... secrets) {
      return secrets(ImmutableList.copyOf(secrets));
    }

    public abstract Builder batchMode(String batchMode);
    public abstract BatchCreateOrUpdateSecretsRequestV2 build();
  }

  @JsonCreator public static BatchCreateOrUpdateSecretsRequestV2 fromParts(
      @JsonProperty("secrets") List<CreateOrUpdateSecretInfoV2> secrets,
      @JsonProperty("batchMode") String batchMode) {
    return builder()
        .batchMode(batchMode)
        .secrets(ImmutableList.copyOf(secrets))
        .build();
  }

  @JsonProperty("secrets") public abstract List<CreateOrUpdateSecretInfoV2> secrets();
  @JsonProperty("batchMode") public abstract String batchMode();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("secrets", secrets())
        .add("batchMode", batchMode())
        .omitNullValues()
        .toString();
  }
}
