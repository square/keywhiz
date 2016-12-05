package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Map;

/**
 * A request to update a secret which explicitly indicates which of its fields are
 * to be considered valid
 */
@AutoValue public abstract class PartialUpdateSecretRequestV2 {
  PartialUpdateSecretRequestV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_PartialUpdateSecretRequestV2.Builder()
        .contentPresent(false)
        .content("")
        .descriptionPresent(false)
        .description("")
        .metadataPresent(false)
        .metadata(ImmutableMap.of())
        .expiryPresent(false)
        .expiry(0L)
        .typePresent(false)
        .type("");
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract boolean contentPresent();
    abstract String content();
    abstract PartialUpdateSecretRequestV2 autoBuild();

    public abstract Builder contentPresent(boolean contentPresent);
    public abstract Builder content(String content);
    public abstract Builder descriptionPresent(boolean descriptionPresent);
    public abstract Builder description(String description);
    public abstract Builder metadataPresent(boolean metadataPresent);
    public abstract Builder metadata(ImmutableMap<String, String> metadata);
    public abstract Builder typePresent(boolean typePresent);
    public abstract Builder type(String type);
    public abstract Builder expiryPresent(boolean expiryPresent);
    public abstract Builder expiry(Long expiry);

    /**
     * @throws IllegalArgumentException if builder data is invalid.
     */
    public PartialUpdateSecretRequestV2 build() {
      if (contentPresent()) {
        // throws IllegalArgumentException if content not valid base64.
        Base64.getDecoder().decode(content());
      }

      return autoBuild();
    }
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static PartialUpdateSecretRequestV2 fromParts(
      @JsonProperty("contentPresent") boolean contentPresent,
      @JsonProperty("content") @Nullable String content,
      @JsonProperty("descriptionPresent") boolean descriptionPresent,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("metadataPresent") boolean metadataPresent,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("expiryPresent") boolean expiryPresent,
      @JsonProperty("expiry") @Nullable Long expiry,
      @JsonProperty("typePresent") boolean typePresent,
      @JsonProperty("type") @Nullable String type) {
    return builder()
        .contentPresent(contentPresent)
        .content(Strings.nullToEmpty(content))
        .descriptionPresent(descriptionPresent)
        .description(Strings.nullToEmpty(description))
        .metadataPresent(metadataPresent)
        .metadata(metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata))
        .expiryPresent(expiryPresent)
        .expiry(expiry == null ? Long.valueOf(0) : expiry)
        .typePresent(typePresent)
        .type(Strings.nullToEmpty(type))
        .build();
  }

  @JsonProperty("contentPresent") public abstract boolean contentPresent();
  @JsonProperty("content") public abstract String content();
  @JsonProperty("descriptionPresent") public abstract boolean descriptionPresent();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("metadataPresent") public abstract boolean metadataPresent();
  @JsonProperty("metadata") public abstract ImmutableMap<String, String> metadata();
  @JsonProperty("expiryPresent") public abstract boolean expiryPresent();
  @JsonProperty("expiry") public abstract Long expiry();
  @JsonProperty("typePresent") public abstract boolean typePresent();
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
