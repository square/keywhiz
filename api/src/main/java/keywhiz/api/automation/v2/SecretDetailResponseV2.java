package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SecretSeriesAndContent;

import static com.google.common.base.Strings.nullToEmpty;

@AutoValue public abstract class SecretDetailResponseV2 {
  SecretDetailResponseV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_SecretDetailResponseV2.Builder()
        .checksum("")
        .description("")
        .type(null)
        .expiry(0)
        .metadata(ImmutableMap.of());
  }

  @AutoValue.Builder public abstract static class Builder {
    public abstract Builder metadata(ImmutableMap<String, String> metadata);
    abstract SecretDetailResponseV2 autoBuild();

    public abstract Builder name(String name);
    public abstract Builder description(String description);
    public abstract Builder checksum(String checksum);
    public abstract Builder createdAtSeconds(long createdAt);
    public abstract Builder createdBy(String createdBy);
    public abstract Builder updatedAtSeconds(long updatedAt);
    public abstract Builder updatedBy(String updatedBy);
    public abstract Builder type(@Nullable String type);
    public abstract Builder expiry(long expiry);
    public abstract Builder version(@Nullable Long version); // Unique ID in secrets_content table
    public abstract Builder contentCreatedAtSeconds(@Nullable Long contentCreatedAt);
    public abstract Builder contentCreatedBy(@Nullable String contentCreatedBy);

    public Builder metadata(Map<String, String> metadata) {
      return metadata(ImmutableMap.copyOf(metadata));
    }

    public Builder seriesAndContent(SecretSeriesAndContent seriesAndContent) {
      return this
          .name(seriesAndContent.series().name())
          .description(seriesAndContent.series().description())
          .checksum(seriesAndContent.content().hmac())
          .createdAtSeconds(seriesAndContent.series().createdAt().toEpochSecond())
          .createdBy(seriesAndContent.series().createdBy())
          .updatedAtSeconds(seriesAndContent.series().updatedAt().toEpochSecond())
          .updatedBy(seriesAndContent.series().updatedBy())
          .metadata(seriesAndContent.content().metadata())
          .type(seriesAndContent.series().type().orElse(null))
          .expiry(seriesAndContent.content().expiry())
          .version(seriesAndContent.series().currentVersion().orElse(null))
          .contentCreatedAtSeconds(seriesAndContent.content().createdAt().toEpochSecond())
          .contentCreatedBy(seriesAndContent.content().createdBy());
    }

    public Builder sanitizedSecret(SanitizedSecret sanitizedSecret) {
      return this
          .name(sanitizedSecret.name())
          .description(sanitizedSecret.description())
          .checksum(sanitizedSecret.checksum())
          .createdAtSeconds(sanitizedSecret.createdAt().toEpochSecond())
          .createdBy(sanitizedSecret.createdBy())
          .updatedAtSeconds(sanitizedSecret.updatedAt().toEpochSecond())
          .updatedBy(sanitizedSecret.updatedBy())
          .metadata(sanitizedSecret.metadata())
          .type(sanitizedSecret.type().orElse(null))
          .expiry(sanitizedSecret.expiry())
          .version(sanitizedSecret.version().orElse(null))
          .contentCreatedAtSeconds(sanitizedSecret.contentCreatedAt().isPresent() ?
              sanitizedSecret.contentCreatedAt().get().toEpochSecond() : null)
          .contentCreatedBy(sanitizedSecret.contentCreatedBy());
    }

    public SecretDetailResponseV2 build() {
      return this.autoBuild();
    }
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static SecretDetailResponseV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("checksum") String checksum,
      @JsonProperty("createdAtSeconds") long createdAtSeconds,
      @JsonProperty("createdBy") String createdBy,
      @JsonProperty("updatedAtSeconds") long updatedAtSeconds,
      @JsonProperty("updatedBy") String updatedBy,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("type") @Nullable String type,
      @JsonProperty("expiry") long expiry,
      @JsonProperty("version") @Nullable Long version,
      @JsonProperty("contentCreatedAtSeconds") @Nullable Long contentCreatedAtSeconds,
      @JsonProperty("contentCreatedBy") @Nullable String contentCreatedBy) {
    return builder()
        .name(name)
        .description(nullToEmpty(description))
        .checksum(checksum)
        .createdAtSeconds(createdAtSeconds)
        .createdBy(createdBy)
        .updatedAtSeconds(updatedAtSeconds)
        .updatedBy(updatedBy)
        .metadata(metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata))
        .type(type)
        .expiry(expiry)
        .version(version)
        .contentCreatedAtSeconds(contentCreatedAtSeconds)
        .contentCreatedBy(contentCreatedBy)
        .build();
  }

  // TODO: Consider Optional values in place of Nullable.
  @JsonProperty("name") public abstract String name();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("checksum") public abstract String checksum();
  @JsonProperty("createdAtSeconds") public abstract long createdAtSeconds();
  @JsonProperty("createdBy") public abstract String createdBy();
  @JsonProperty("updatedAtSeconds") public abstract long updatedAtSeconds();
  @JsonProperty("updatedBy") public abstract String updatedBy();
  @JsonProperty("metadata") public abstract ImmutableMap<String, String> metadata();
  @JsonProperty("type") @Nullable public abstract String type();
  @JsonProperty("expiry") public abstract long expiry();
  @JsonProperty("version") @Nullable public abstract Long version();
  @JsonProperty("contentCreatedAtSeconds") @Nullable public abstract Long contentCreatedAtSeconds();
  @JsonProperty("contentCreatedBy") @Nullable public abstract String contentCreatedBy();

  @Override public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name())
        .add("description", description())
        .add("checksum", checksum())
        .add("createdAtSeconds", createdAtSeconds())
        .add("createdBy", createdBy())
        .add("updatedAtSeconds", updatedAtSeconds())
        .add("updatedBy", updatedBy())
        .add("type", type())
        .add("metadata", metadata())
        .add("expiry", expiry())
        .add("version", version())
        .add("contentCreatedAtSeconds", contentCreatedAtSeconds())
        .add("contentCreatedBy", contentCreatedBy())
        .omitNullValues()
        .toString();
  }
}

