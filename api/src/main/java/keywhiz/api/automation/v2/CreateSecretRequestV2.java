package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Base64;
import java.util.Map;
import javax.annotation.Nullable;
import keywhiz.api.validation.ValidBase64;
import org.hibernate.validator.constraints.NotEmpty;

@AutoValue public abstract class CreateSecretRequestV2 {
  CreateSecretRequestV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_CreateSecretRequestV2.Builder()
        .description("")
        .versioned(false) // may flip default in the future
        .metadata(ImmutableMap.of())
        .type("")
        .groups();
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract String content();
    abstract Builder groups(ImmutableSet<String> groups);
    abstract CreateSecretRequestV2 autoBuild();

    public abstract Builder name(String name);
    public abstract Builder content(String content);
    public abstract Builder description(String description);
    public abstract Builder versioned(boolean versioned);
    public abstract Builder metadata(ImmutableMap<String, String> metadata);
    public abstract Builder type(String type);

    public Builder groups(String... groups) {
      return groups(ImmutableSet.copyOf(groups));
    }

    public Builder groups(Iterable<String> groups) {
      return groups(ImmutableSet.copyOf(groups));
    }

    /**
     * @throws IllegalArgumentException if builder data is invalid.
     */
    public CreateSecretRequestV2 build() {
      // throws IllegalArgumentException if content not valid base64.
      Base64.getDecoder().decode(content());
      return autoBuild();
    }
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static CreateSecretRequestV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("content") String content,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("withVersion") boolean withVersion,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("type") @Nullable String type,
      @JsonProperty("groups") @Nullable Iterable<String> groups) {
    return builder()
        .name(name)
        .content(content)
        .versioned(withVersion)
        .description(Strings.nullToEmpty(description))
        .metadata(metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata))
        .type(Strings.nullToEmpty(type))
        .groups(groups == null ? ImmutableSet.of() : groups)
        .build();
  }

  @JsonProperty("name") @NotEmpty public abstract String name();
  @JsonProperty("content") @NotEmpty @ValidBase64 public abstract String content();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("versioned") public abstract boolean versioned();
  @JsonProperty("metadata") public abstract ImmutableMap<String, String> metadata();
  @JsonProperty("type") public abstract String type();
  @JsonProperty("groups") public abstract ImmutableSet<String> groups();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name())
        .add("content", "[REDACTED]")
        .add("description", description())
        .add("withVersion", versioned())
        .add("metadata", metadata())
        .add("type", type())
        .add("groups", groups())
        .omitNullValues()
        .toString();
  }
}
