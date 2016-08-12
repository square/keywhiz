package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;

import static com.google.common.base.Strings.nullToEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static keywhiz.api.model.Secret.decodedLength;

@AutoValue public abstract class SecretDetailResponseV2 {
  SecretDetailResponseV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_SecretDetailResponseV2.Builder()
        .content("")
        .description("")
        .type(null)
        .metadata(ImmutableMap.of());
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract String content();
    abstract Builder size(UnsignedLong size);
    public abstract Builder metadata(ImmutableMap<String, String> metadata);
    abstract SecretDetailResponseV2 autoBuild();

    public abstract Builder name(String name);
    public abstract Builder version(@Nullable long version); // Unique ID in secrets_content table
    public abstract Builder content(String secret);
    public abstract Builder description(String description);
    public abstract Builder createdAtSeconds(long createdAt);
    public abstract Builder createdBy(String person);
    public abstract Builder type(@Nullable String type);
    public abstract Builder expiry(long expiry);

    public Builder metadata(Map<String, String> metadata) {
      return metadata(ImmutableMap.copyOf(metadata));
    }

    public Builder series(SecretSeries series) {
      return this
          .name(series.name())
          .version(series.currentVersion().orElse(null))
          .description(series.description())
          .createdAtSeconds(series.createdAt().toEpochSecond())
          .createdBy(series.createdBy())
          .type(series.type().orElse(null));
    }

    public Builder secret(Secret secret) {
      return this
          .name(secret.getName())
          .description(secret.getDescription())
          .content(secret.getSecret())
          .createdAtSeconds(secret.getCreatedAt().toEpochSecond())
          .createdBy(secret.getCreatedBy())
          .type(secret.getType().orElse(null))
          .expiry(secret.getExpiry())
          .metadata(secret.getMetadata());
    }

    public Builder secretContent(SecretContent secretContent) {
      return this
          .version(secretContent.id())
          .content("")
          .createdAtSeconds(secretContent.createdAt().toEpochSecond())
          .createdBy(secretContent.createdBy())
          .metadata(secretContent.metadata())
          .expiry(secretContent.expiry());

    }

    public SecretDetailResponseV2 build() {
      // throws IllegalArgumentException if content not base64
      Base64.getDecoder().decode(content());
      return this
          .size(UnsignedLong.valueOf(decodedLength(content())))
          .autoBuild();
    }
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static SecretDetailResponseV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("version") @Nullable long version,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("content") String content,
      @JsonProperty("size") UnsignedLong size,
      @JsonProperty("createdAtSeconds") long createdAtSeconds,
      @JsonProperty("createdBy") String createdBy,
      @JsonProperty("type") @Nullable String type,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("expiry") @Nullable long expiry) {
    return builder()
        .name(name)
        .version(version)
        .description(nullToEmpty(description))
        .content(content)
        .size(size)
        .createdAtSeconds(createdAtSeconds)
        .createdBy(createdBy)
        .type(type)
        .metadata(metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata))
        .expiry(expiry)
        .build();
  }

  // TODO: Consider Optional values in place of Nullable.
  @JsonProperty("name") public abstract String name();
  @JsonProperty("version") @Nullable public abstract long version();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("content") public abstract String content();
  @JsonProperty("size") public abstract UnsignedLong size();
  @JsonProperty("createdAtSeconds") public abstract long createdAtSeconds();
  @JsonProperty("createdBy") public abstract String createdBy();
  @JsonProperty("type") @Nullable public abstract String type();
  @JsonProperty("metadata") public abstract ImmutableMap<String, String> metadata();
  @JsonProperty("expiry") public abstract long expiry();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name())
        .add("version", version())   // TODO: Should we remove this so it's not as exposed to the user?
        .add("description", description())
        .add("content", "[REDACTED]")
        .add("size", size())
        .add("createdAtSeconds", createdAtSeconds())
        .add("createdBy", createdBy())
        .add("type", type())
        .add("metadata", metadata())
        .add("expiry", expiry())
        .omitNullValues()
        .toString();
  }
}
