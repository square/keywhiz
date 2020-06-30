package keywhiz.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Batch Secret Request.
 */
public class BatchSecretRequest {
  @NotEmpty
  @JsonProperty
  public final ImmutableList<String> secrets;

  public BatchSecretRequest(@JsonProperty("secrets") ImmutableList<String> secrets) {
    this.secrets = secrets;
  }

  @Override public int hashCode() {
    return Objects.hashCode(secrets);
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof BatchSecretRequest) {
      BatchSecretRequest that = (BatchSecretRequest) o;
      if (Objects.equal(this.secrets, that.secrets)) {
        return true;
      }
    }
    return false;
  }
}
