package keywhiz.api;

import autovalue.shaded.org.checkerframework$.checker.nullness.qual.$MonotonicNonNull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Batch Secret Request.
 */
@AutoValue
public abstract class BatchSecretRequest {
  public static BatchSecretRequest create(ImmutableList<String> secrets) {
    return new AutoValue_BatchSecretRequest(secrets);
  }

  @JsonProperty("secrets") public abstract ImmutableList<String> secrets();

  @JsonCreator public static BatchSecretRequest forName(@JsonProperty("secrets") ImmutableList<String> secrets) {
    return new AutoValue_BatchSecretRequest(secrets);
  }

}
