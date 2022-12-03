package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.Base64;
import java.util.Map;
import javax.annotation.Nullable;
import keywhiz.api.validation.ValidBase64;

@AutoValue public abstract class CreateOrUpdateSecretInfoV2 {
  CreateOrUpdateSecretInfoV2() {}

  public static CreateOrUpdateSecretInfoV2.Builder builder() {
    return new AutoValue_CreateOrUpdateSecretInfoV2.Builder()
        .description("")
        .metadata(ImmutableMap.of())
        .expiry(0)
        .type("");
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract String content();
    abstract CreateOrUpdateSecretInfoV2 autoBuild();

    public abstract Builder name(String name);
    public abstract Builder content(String content);
    public abstract Builder description(String description);
    public abstract Builder metadata(ImmutableMap<String, String> metadata);
    public abstract Builder type(String type);
    public abstract Builder expiry(long expiry);
    public abstract Builder owner(String owner);

    /**
     * @throws IllegalArgumentException if builder data is invalid.
     */
    public CreateOrUpdateSecretInfoV2 build() {
      // throws IllegalArgumentException if content not valid base64.
      Base64.getDecoder().decode(content());

      CreateOrUpdateSecretInfoV2 info = autoBuild();
      return info;
    }
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static CreateOrUpdateSecretInfoV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("content") String content,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("expiry") long expiry,
      @JsonProperty("type") @Nullable String type,
      @JsonProperty("owner") @Nullable String owner) {
    return builder()
        .name(name)
        .content(content)
        .description(Strings.nullToEmpty(description))
        .metadata(metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata))
        .expiry(expiry)
        .type(Strings.nullToEmpty(type))
        .owner(owner)
        .build();
  }

  @JsonProperty("name") public abstract String name();
  @JsonProperty("content") @ValidBase64 public abstract String content();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("metadata") public abstract ImmutableMap<String, String> metadata();
  @JsonProperty("expiry") public abstract long expiry();
  @JsonProperty("type") public abstract String type();
  @JsonProperty("owner") @Nullable public abstract String owner();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name())
        .add("content", "[REDACTED]")
        .add("description", description())
        .add("metadata", metadata())
        .add("expiry", expiry())
        .add("type", type())
        .add("owner", owner())
        .omitNullValues()
        .toString();
  }
}
