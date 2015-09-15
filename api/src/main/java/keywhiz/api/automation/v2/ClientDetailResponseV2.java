package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.time.OffsetDateTime;
import keywhiz.api.model.Client;

@AutoValue public abstract class ClientDetailResponseV2 {
  ClientDetailResponseV2() {} // prevent sub-classing

  public static ClientDetailResponseV2 fromClient(Client client) {
    return new AutoValue_ClientDetailResponseV2(
        client.getName(),
        client.getDescription(),
        client.getCreatedAt(),
        client.getUpdatedAt(),
        client.getCreatedBy(),
        client.getUpdatedBy());
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static ClientDetailResponseV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("creationDate") OffsetDateTime creationDate,
      @JsonProperty("updateDate") OffsetDateTime updateDate,
      @JsonProperty("createdBy") String createdBy,
      @JsonProperty("updatedBy") String updatedBy) {
    return new AutoValue_ClientDetailResponseV2(name, description, creationDate, updateDate, createdBy, updatedBy);
  }

  @JsonProperty("name") public abstract String name();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("creationDate") public abstract OffsetDateTime creationDate();
  @JsonProperty("updateDate") public abstract OffsetDateTime updateDate();
  @JsonProperty("createdBy") public abstract String createdBy();
  @JsonProperty("updatedBy") public abstract String updatedBy();
}
