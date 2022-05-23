package keywhiz.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/**
 * Batch Secret Request.
 */
@AutoValue
public abstract class BatchSecretRequest {
  public static BatchSecretRequest create(ImmutableList<String> secrets) {
    return new AutoValue_BatchSecretRequest(secrets);
  }

  @JsonProperty("secrets") public abstract ImmutableList<String> secrets();

  @JsonCreator public static BatchSecretRequest forSecrets(@JsonProperty("secrets") ImmutableList<String> secrets) {
    return new AutoValue_BatchSecretRequest(secrets);
  }

}
