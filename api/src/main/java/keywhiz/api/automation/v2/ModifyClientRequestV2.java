package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue public abstract class ModifyClientRequestV2 {
  @JsonCreator public static ModifyClientRequestV2 forName(@JsonProperty("name") String name) {
    return new AutoValue_ModifyClientRequestV2(name);
  }

  @JsonProperty("name") public abstract String name();
}
