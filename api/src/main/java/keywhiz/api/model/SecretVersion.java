package keywhiz.api.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import javax.annotation.Nullable;
import keywhiz.api.ApiDate;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Contains all relevant information on a version of a secret (note that this does
 * not contain the contents of a secret).
 */
@AutoValue
public abstract class SecretVersion {
  public static SecretVersion of(long secretId, long versionId, String name,
      @Nullable String description, String checksum, ApiDate createdAt,
      @Nullable String createdBy, ApiDate updatedAt, @Nullable String updatedBy,
      ImmutableMap<String, String> metadata, @Nullable String type, long expiry) {
    return new AutoValue_SecretVersion(secretId, versionId, name, nullToEmpty(description),
        checksum, createdAt, nullToEmpty(createdBy), updatedAt,
        nullToEmpty(updatedBy), metadata, type, expiry);
  }

  public abstract long secretId();
  public abstract long versionId();
  public abstract String name();
  public abstract String description();
  public abstract String checksum();
  public abstract ApiDate createdAt();
  public abstract String createdBy();
  public abstract ApiDate updatedAt();
  public abstract String updatedBy();
  @JsonAnyGetter public abstract  ImmutableMap<String, String> metadata();
  public abstract String type();
  public abstract long expiry();

  @Override public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("secretId", secretId())
        .add("name", name())
        .add("description", description())
        .add("checksum", checksum())
        .add("createdAt", createdAt())
        .add("createdBy", createdBy())
        .add("updatedAt", updatedAt())
        .add("updatedBy", updatedBy())
        .add("metadata", metadata())
        .add("type", type())
        .add("expiry", expiry())
        .omitNullValues().toString();
  }
}
