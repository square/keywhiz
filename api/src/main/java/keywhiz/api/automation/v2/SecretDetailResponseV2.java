package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import java.util.Base64;
import java.util.Map;
import javax.annotation.Nullable;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.api.model.SecretVersion;

import static com.google.common.base.Strings.nullToEmpty;
import static keywhiz.api.model.Secret.decodedLength;

@AutoValue public abstract class SecretDetailResponseV2 {
  SecretDetailResponseV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_SecretDetailResponseV2.Builder()
        .content("")
        .checksum("")
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
    public abstract Builder checksum(String checksum);
    public abstract Builder description(String description);
    public abstract Builder createdAtSeconds(long createdAt);
    public abstract Builder createdBy(String person);
    public abstract Builder updatedAtSeconds(long updatedAt);
    public abstract Builder updatedBy(String person);
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
          .updatedAtSeconds(series.updatedAt().toEpochSecond())
          .updatedBy(series.updatedBy())
          .type(series.type().orElse(null));
    }

    // Does not save secret contents, but saves HMAC
    public Builder seriesAndContent(SecretSeriesAndContent seriesAndContent) {
      return this
          .name(seriesAndContent.series().name())
          .version(seriesAndContent.series().currentVersion().orElse(null))
          .checksum(seriesAndContent.content().hmac())
          .description(seriesAndContent.series().description())
          .metadata(seriesAndContent.content().metadata())
          .createdAtSeconds(seriesAndContent.series().createdAt().toEpochSecond())
          .createdBy(seriesAndContent.series().createdBy())
          .updatedAtSeconds(seriesAndContent.series().updatedAt().toEpochSecond())
          .updatedBy(seriesAndContent.series().updatedBy())
          .expiry(seriesAndContent.content().expiry())
          .type(seriesAndContent.series().type().orElse(null));
    }

    public Builder secret(Secret secret) {
      return this
          .name(secret.getName())
          .description(secret.getDescription())
          .content(secret.getSecret())
          .checksum(secret.getChecksum())
          .createdAtSeconds(secret.getCreatedAt().toEpochSecond())
          .createdBy(secret.getCreatedBy())
          .updatedAtSeconds(secret.getUpdatedAt().toEpochSecond())
          .updatedBy(secret.getUpdatedBy())
          .type(secret.getType().orElse(null))
          .expiry(secret.getExpiry())
          .metadata(secret.getMetadata());
    }

    // Does not save secret contents or checksum
    public Builder secretVersion(SecretVersion secretVersion) {
      return this
          .name(secretVersion.name())
          .version(secretVersion.versionId())
          .description(secretVersion.description())
          .checksum(secretVersion.checksum())
          .createdAtSeconds(secretVersion.createdAt().toEpochSecond())
          .createdBy(secretVersion.createdBy())
          .updatedAtSeconds(secretVersion.updatedAt().toEpochSecond())
          .updatedBy(secretVersion.updatedBy())
          .type(secretVersion.type().orElse(null))
          .expiry(secretVersion.expiry())
          .metadata(secretVersion.metadata());
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
      @JsonProperty("checksum") String checksum,
      @JsonProperty("size") UnsignedLong size,
      @JsonProperty("createdAtSeconds") long createdAtSeconds,
      @JsonProperty("createdBy") String createdBy,
      @JsonProperty("updatedAtSeconds") long updatedAtSeconds,
      @JsonProperty("updatedBy") String updatedBy,
      @JsonProperty("type") @Nullable String type,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("expiry") @Nullable long expiry) {
    return builder()
        .name(name)
        .version(version)
        .description(nullToEmpty(description))
        .content(content)
        .checksum(checksum)
        .size(size)
        .createdAtSeconds(createdAtSeconds)
        .createdBy(createdBy)
        .updatedAtSeconds(updatedAtSeconds)
        .updatedBy(updatedBy)
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
  @JsonProperty("checksum") public abstract String checksum();
  @JsonProperty("size") public abstract UnsignedLong size();
  @JsonProperty("createdAtSeconds") public abstract long createdAtSeconds();
  @JsonProperty("createdBy") public abstract String createdBy();
  @JsonProperty("updatedAtSeconds") public abstract long updatedAtSeconds();
  @JsonProperty("updatedBy") public abstract String updatedBy();
  @JsonProperty("type") @Nullable public abstract String type();
  @JsonProperty("metadata") public abstract ImmutableMap<String, String> metadata();
  @JsonProperty("expiry") public abstract long expiry();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name())
        .add("description", description())
        .add("content", "[REDACTED]")
        .add("checksum", checksum())
        .add("size", size())
        .add("createdAtSeconds", createdAtSeconds())
        .add("createdBy", createdBy())
        .add("updatedAtSeconds", updatedAtSeconds())
        .add("updatedBy", updatedBy())
        .add("type", type())
        .add("metadata", metadata())
        .add("expiry", expiry())
        .omitNullValues()
        .toString();
  }
}

