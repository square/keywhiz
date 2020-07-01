package keywhiz.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import javax.validation.constraints.NotNull;

/**
 * Batch Secret Request.
 */
@AutoValue
public abstract class BatchSecretRequest {
  public static BatchSecretRequest create(ImmutableList<String> secrets) {
    return new AutoValue_BatchSecretRequest(secrets);
  }

  @JsonProperty("secrets")
  @NotNull
  public abstract ImmutableList<String> secrets();


}
