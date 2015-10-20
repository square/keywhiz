package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

import static com.google.common.base.Strings.nullToEmpty;

@AutoValue public abstract class CreateGroupRequestV2 {
  CreateGroupRequestV2() {} // prevent sub-classing

  public static Builder builder() {
    return new AutoValue_CreateGroupRequestV2.Builder()
        .description("");
  }

  @AutoValue.Builder public abstract static class Builder {
    public abstract Builder name(String name);
    public abstract Builder description(String description);
    abstract CreateGroupRequestV2 autoBuild();

    public CreateGroupRequestV2 build() {
      CreateGroupRequestV2 request = autoBuild();
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
  @JsonCreator public static CreateGroupRequestV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description) {
    return builder()
        .name(name)
        .description(nullToEmpty(description))
        .build();
  }

  @JsonProperty("name") public abstract String name();
  @JsonProperty("description") public abstract String description();
}
