package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;

@AutoValue public class BatchCreateOrUpdateSecretsResponseV2 {
  BatchCreateOrUpdateSecretsResponseV2() {}

  public static BatchCreateOrUpdateSecretsResponseV2.Builder builder() {
    return new AutoValue_BatchCreateOrUpdateSecretsResponseV2.Builder();
  }

  @AutoValue.Builder public abstract static class Builder {
    public abstract BatchCreateOrUpdateSecretsResponseV2 build();
  }

  @JsonCreator public static BatchCreateOrUpdateSecretsResponseV2 fromParts() {
    return builder()
        .build();
  }

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .omitNullValues()
        .toString();
  }
}
