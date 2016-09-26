package keywhiz.api.automation.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;

import java.util.Optional;

@AutoValue public abstract class ClientDetailResponseV2 {
  ClientDetailResponseV2() {} // prevent sub-classing

  public static ClientDetailResponseV2 fromClient(Client client) {
    Optional<ApiDate> lastSeen = Optional.ofNullable(client.getLastSeen());

    return new AutoValue_ClientDetailResponseV2(
        client.getName(),
        client.getDescription(),
        client.getCreatedAt().toEpochSecond(),
        client.getUpdatedAt().toEpochSecond(),
        client.getCreatedBy(),
        client.getUpdatedBy(),
        lastSeen.map(ApiDate::toEpochSecond)
    );
  }

  /**
   * Static factory method used by Jackson for deserialization
   */
  @SuppressWarnings("unused")
  @JsonCreator public static ClientDetailResponseV2 fromParts(
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("createdAtSeconds") long createdAtSeconds,
      @JsonProperty("updatedAtSeconds") long updatedAtSeconds,
      @JsonProperty("createdBy") String createdBy,
      @JsonProperty("updatedBy") String updatedBy,
      @JsonProperty("lastSeenSeconds") Optional<Long> lastSeenSeconds) {
    return new AutoValue_ClientDetailResponseV2(name, description, createdAtSeconds, updatedAtSeconds, createdBy, updatedBy, lastSeenSeconds);
  }

  @JsonProperty("name") public abstract String name();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("createdAtSeconds") public abstract long createdAtSeconds();
  @JsonProperty("updatedAtSeconds") public abstract long updatedAtSeconds();
  @JsonProperty("createdBy") public abstract String createdBy();
  @JsonProperty("updatedBy") public abstract String updatedBy();
  @JsonProperty("lastSeenSeconds") public abstract Optional<Long> lastSeenSeconds();
}
