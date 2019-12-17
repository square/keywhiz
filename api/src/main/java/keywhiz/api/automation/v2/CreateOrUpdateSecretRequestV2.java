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

@AutoValue public abstract class CreateOrUpdateSecretRequestV2 {
  CreateOrUpdateSecretRequestV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_CreateOrUpdateSecretRequestV2.Builder()
        .description("")
        .metadata(ImmutableMap.of())
        .expiry(0)
        .type("");
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract String content();
    abstract CreateOrUpdateSecretRequestV2 autoBuild();

    public abstract Builder content(String content);
    public abstract Builder description(String description);
    public abstract Builder metadata(ImmutableMap<String, String> metadata);
    public abstract Builder type(String type);
    public abstract Builder expiry(long expiry);

    /**
     * @throws IllegalArgumentException if builder data is invalid.
     */
    public CreateOrUpdateSecretRequestV2 build() {
      // throws IllegalArgumentException if content not valid base64.
      Base64.getDecoder().decode(content());

      CreateOrUpdateSecretRequestV2 request = autoBuild();
      return request;
    }
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static CreateOrUpdateSecretRequestV2 fromParts(
      @JsonProperty("content") String content,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("expiry") long expiry,
      @JsonProperty("type") @Nullable String type) {
    return builder()
        .content(content)
        .description(Strings.nullToEmpty(description))
        .metadata(metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata))
        .expiry(expiry)
        .type(Strings.nullToEmpty(type))
        .build();
  }

  @JsonProperty("content") @ValidBase64 public abstract String content();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("metadata") public abstract ImmutableMap<String, String> metadata();
  @JsonProperty("expiry") public abstract long expiry();
  @JsonProperty("type") public abstract String type();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("content", "[REDACTED]")
        .add("description", description())
        .add("metadata", metadata())
        .add("expiry", expiry())
        .add("type", type())
        .omitNullValues()
        .toString();
  }
}
