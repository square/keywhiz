package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import keywhiz.api.validation.ValidBase64;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Map;

@AutoValue public abstract class CloneSecretRequestV2 {
  CloneSecretRequestV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_CloneSecretRequestV2.Builder()
        .name("")
        .newName("");
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract CloneSecretRequestV2 autoBuild();

    public abstract Builder name(String name);
    public abstract Builder newName(String newName);

    /**
     * @throws IllegalArgumentException if builder data is invalid.
     */
    public CloneSecretRequestV2 build() {
      // throws IllegalArgumentException if content not valid base64.
      return autoBuild();
    }
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static CloneSecretRequestV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("newName") String newName) {
    return builder()
        .name(name)
        .newName(newName)
        .build();
  }

  @JsonProperty("name") public abstract String name();
  @JsonProperty("newName") public abstract String newName();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name())
        .add("newName", newName())
        .toString();
  }
}
