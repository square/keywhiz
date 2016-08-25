package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;

@AutoValue public abstract class SetSecretVersionRequestV2 {
  SetSecretVersionRequestV2() {
  } // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_SetSecretVersionRequestV2.Builder()
        .name("")
        .version(0);
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract SetSecretVersionRequestV2 autoBuild();

    public abstract Builder name(String name);

    public abstract Builder version(long version);

    /**
     * @throws IllegalArgumentException if builder data is invalid.
     */
    public SetSecretVersionRequestV2 build() {
      SetSecretVersionRequestV2 request = autoBuild();
      if (request.name().isEmpty()) {
        throw new IllegalStateException("name is empty");
      }
      return request;
    }
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static SetSecretVersionRequestV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("version") long version) {
    return builder()
        .name(name)
        .version(version)
        .build();
  }

  @JsonProperty("name") public abstract String name();

  @JsonProperty("version") public abstract long version();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name())
        .add("version", version())
        .omitNullValues()
        .toString();
  }
}
