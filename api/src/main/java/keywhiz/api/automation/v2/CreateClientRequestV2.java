package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import javax.annotation.Nullable;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Model for request to create a new keywhiz client.
 *
 * Note: renamed from CreateClientRequest because swagger docs do not handle class name collisions.
 */
@AutoValue public abstract class CreateClientRequestV2 {
  CreateClientRequestV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_CreateClientRequestV2.Builder()
        .groups()
        .description("")
        .spiffeId("");
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract Builder groups(ImmutableSet<String> groups);

    public abstract Builder name(String name);
    public abstract Builder description(String description);
    public abstract Builder spiffeId(String spiffeId);
    public abstract Builder owner(String owner);

    abstract CreateClientRequestV2 autoBuild();

    public Builder groups(String... groups) {
      return groups(ImmutableSet.copyOf(groups));
    }

    public CreateClientRequestV2 build() {
      CreateClientRequestV2 request = autoBuild();
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
  @JsonCreator public static CreateClientRequestV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("groups") @Nullable Iterable<String> groups,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("spiffeId") @Nullable String spiffeId,
      @JsonProperty("owner") @Nullable String owner) {
    return builder()
        .name(name)
        .groups(groups == null ? ImmutableSet.of() : ImmutableSet.copyOf(groups))
        .description(nullToEmpty(description))
        .spiffeId(nullToEmpty(spiffeId))
        .owner(owner)
        .build();
  }

  @JsonProperty("name") public abstract String name();
  @JsonProperty("groups") public abstract ImmutableSet<String> groups();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("spiffeId") public abstract String spiffeId();
  @JsonProperty("owner") @Nullable public abstract String owner();
}
