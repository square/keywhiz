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

@AutoValue public abstract class CreateSecretRequestV2 {
  CreateSecretRequestV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_CreateSecretRequestV2.Builder()
        .description("")
        .metadata(ImmutableMap.of())
        .expiry(0)
        .type("")
        .groups();
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract String content();
    abstract Builder groups(ImmutableSet<String> groups);
    abstract CreateSecretRequestV2 autoBuild();

    public abstract Builder name(String name);
    public abstract Builder owner(String owner);
    public abstract Builder content(String content);
    public abstract Builder description(String description);
    public abstract Builder metadata(ImmutableMap<String, String> metadata);
    public abstract Builder type(String type);
    public abstract Builder expiry(long expiry);

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

      CreateSecretRequestV2 request = autoBuild();
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
  @JsonCreator public static CreateSecretRequestV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("owner") @Nullable String owner,
      @JsonProperty("content") String content,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("expiry") long expiry,
      @JsonProperty("type") @Nullable String type,
      @JsonProperty("groups") @Nullable Iterable<String> groups) {
    return builder()
        .name(name)
        .owner(owner)
        .content(content)
        .description(Strings.nullToEmpty(description))
        .metadata(metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata))
        .expiry(expiry)
        .type(Strings.nullToEmpty(type))
        .groups(groups == null ? ImmutableSet.of() : ImmutableSet.copyOf(groups))
        .build();
  }

  @JsonProperty("name") public abstract String name();
  @JsonProperty("owner") @Nullable public abstract String owner();
  @JsonProperty("content") @ValidBase64 public abstract String content();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("metadata") public abstract ImmutableMap<String, String> metadata();
  @JsonProperty("expiry") public abstract long expiry();
  @JsonProperty("type") public abstract String type();
  @JsonProperty("groups") public abstract ImmutableSet<String> groups();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name())
        .add("owner", owner())
        .add("content", "[REDACTED]")
        .add("description", description())
        .add("metadata", metadata())
        .add("expiry", expiry())
        .add("type", type())
        .add("groups", groups())
        .omitNullValues()
        .toString();
  }
}
