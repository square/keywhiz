package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

@AutoValue public abstract class ModifyGroupsRequestV2 {
  ModifyGroupsRequestV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_ModifyGroupsRequestV2.Builder()
        .addGroups()
        .removeGroups();
  }

  @AutoValue.Builder public abstract static class Builder {
    // intended to be package-private
    abstract Builder addGroups(ImmutableSet<String> groups);
    abstract Builder removeGroups(ImmutableSet<String> groups);

    public Builder addGroups(String... groups) {
      return addGroups(ImmutableSet.copyOf(groups));
    }

    public Builder removeGroups(String... groups) {
      return removeGroups(ImmutableSet.copyOf(groups));
    }

    public abstract ModifyGroupsRequestV2 build();
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static ModifyGroupsRequestV2 fromParts(
      @JsonProperty("addGroups") ImmutableSet<String> addGroups,
      @JsonProperty("removeGroups") ImmutableSet<String> removeGroups) {
    return builder().addGroups(addGroups).removeGroups(removeGroups).build();
  }

  @JsonProperty("addGroups") public abstract ImmutableSet<String> addGroups();
  @JsonProperty("removeGroups") public abstract ImmutableSet<String> removeGroups();
}
