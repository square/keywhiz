package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import javax.annotation.Nullable;
import keywhiz.api.model.Group;

@AutoValue public abstract class GroupDetailResponseV2 {
  GroupDetailResponseV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_GroupDetailResponseV2.Builder();
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract Builder name(String name);
    abstract Builder description(String description);
    abstract Builder createdAtSeconds(long createdAtSeconds);
    abstract Builder updatedAtSeconds(long updatedAtSeconds);
    abstract Builder createdBy(String person);
    abstract Builder updatedBy(String person);
    abstract Builder secrets(ImmutableSet<String> secrets);
    abstract Builder clients(ImmutableSet<String> clients);
    abstract Builder metadata(ImmutableMap<String, String> metadata);
    abstract Builder owner(String owner);

    public Builder group(Group group) {
      return this
          .name(group.getName())
          .description(group.getDescription())
          .createdAtSeconds(group.getCreatedAt().toEpochSecond())
          .updatedAtSeconds(group.getUpdatedAt().toEpochSecond())
          .createdBy(group.getCreatedBy())
          .updatedBy(group.getUpdatedBy())
          .metadata(group.getMetadata())
          .owner(group.getOwner());
    }

    public Builder secrets(Iterable<String> secrets) {
      return secrets(ImmutableSet.copyOf(secrets));
    }

    public Builder clients(Iterable<String> clients) {
      return clients(ImmutableSet.copyOf(clients));
    }

    public abstract GroupDetailResponseV2 build();
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static GroupDetailResponseV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("createdAtSeconds") long createdAtSeconds,
      @JsonProperty("updatedAtSeconds") long updatedAtSeconds,
      @JsonProperty("createdBy") String createdBy,
      @JsonProperty("updatedBy") String updatedBy,
      @JsonProperty("secrets") Iterable<String> secrets,
      @JsonProperty("clients") Iterable<String> clients,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("owner") @Nullable String owner) {
    return builder()
        .name(name)
        .description(description)
        .createdAtSeconds(createdAtSeconds)
        .updatedAtSeconds(updatedAtSeconds)
        .createdBy(createdBy)
        .updatedBy(updatedBy)
        .secrets(secrets)
        .clients(clients)
        .metadata(metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata))
        .owner(owner)
        .build();
  }

  @JsonProperty("name") public abstract String name();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("createdAtSeconds") public abstract long createdAtSeconds();
  @JsonProperty("updatedAtSeconds") public abstract long updatedAtSeconds();
  @JsonProperty("createdBy") public abstract String createdBy();
  @JsonProperty("updatedBy") public abstract String updatedBy();
  @JsonProperty("secrets") public abstract ImmutableSet<String> secrets();
  @JsonProperty("clients") public abstract ImmutableSet<String> clients();
  @JsonProperty("metadata") public abstract ImmutableMap<String, String> metadata();
  @JsonProperty("owner") @Nullable public abstract String owner();
}
