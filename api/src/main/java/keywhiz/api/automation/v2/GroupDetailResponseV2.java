package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.time.OffsetDateTime;
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
    abstract Builder creationDate(OffsetDateTime dateTime);
    abstract Builder updateDate(OffsetDateTime dateTime);
    abstract Builder createdBy(String person);
    abstract Builder updatedBy(String person);
    abstract Builder secrets(ImmutableSet<String> secrets);
    abstract Builder clients(ImmutableSet<String> clients);

    public Builder group(Group group) {
      return this
          .name(group.getName())
          .description(group.getDescription())
          .creationDate(group.getCreatedAt())
          .updateDate(group.getUpdatedAt())
          .createdBy(group.getCreatedBy())
          .updatedBy(group.getUpdatedBy());
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
      @JsonProperty("creationDate") OffsetDateTime creationDate,
      @JsonProperty("updateDate") OffsetDateTime updateDate,
      @JsonProperty("createdBy") String createdBy,
      @JsonProperty("updatedBy") String updatedBy,
      @JsonProperty("secrets") Iterable<String> secrets,
      @JsonProperty("clients") Iterable<String> clients) {
    return builder()
        .name(name)
        .description(description)
        .creationDate(creationDate)
        .updateDate(updateDate)
        .createdBy(createdBy)
        .updatedBy(updatedBy)
        .secrets(secrets)
        .clients(clients)
        .build();
  }

  @JsonProperty("name") public abstract String name();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("creationDate") public abstract OffsetDateTime creationDate();
  @JsonProperty("updateDate") public abstract OffsetDateTime updateDate();
  @JsonProperty("createdBy") public abstract String createdBy();
  @JsonProperty("updatedBy") public abstract String updatedBy();
  @JsonProperty("secrets") public abstract ImmutableSet<String> secrets();
  @JsonProperty("clients") public abstract ImmutableSet<String> clients();
}
