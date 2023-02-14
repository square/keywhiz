package keywhiz.service.daos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Group;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.DeletedSecretsRecord;
import org.jooq.DSLContext;

public class SharedSecretSeriesMapper {
  private final TypeReference<Map<String, String>> MAP_STRING_STRING_TYPE =
      new TypeReference<>() {
      };

  private final ObjectMapper mapper;
  private final GroupDAO groupDAO;

  public SharedSecretSeriesMapper(
      ObjectMapper mapper,
      GroupDAO groupDAO) {
    this.mapper = mapper;
    this.groupDAO = groupDAO;
  }

  public SecretSeries map(
      Long ownerId,
      String secretName,
      Long secretId,
      @Nullable String description,
      Long createdAtTimestamp,
      @Nullable String createdBy,
      Long updatedAtTimestamp,
      @Nullable String updatedBy,
      @Nullable String type,
      @Nullable String options,
      Long currentContentsId
  ) {
    String ownerName = getOwnerName(
        ownerId,
        secretName,
        secretId
    );

    return SecretSeries.of(
        secretId,
        secretName,
        ownerName,
        description,
        new ApiDate(createdAtTimestamp),
        createdBy,
        new ApiDate(updatedAtTimestamp),
        updatedBy,
        type,
        tryToReadMapValue(options),
        currentContentsId
    );
  }

  private String getOwnerName(Long ownerId, String secretName, Long secretId) {
    if (ownerId == null) {
      return null;
    }

    Optional<Group> maybeGroup = groupDAO.getGroupById(ownerId);
    if (maybeGroup.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "Unable to find owner for secret [%s] (ID %s): group ID %s not found",
              secretName,
              secretId,
              ownerId));
    }

    return maybeGroup.get().getName();
  }

  private Map<String, String> tryToReadMapValue(@Nullable String options) {
    if (!options.isEmpty()) {
      try {
        return mapper.readValue(options, MAP_STRING_STRING_TYPE);
      } catch (IOException e) {
        throw new RuntimeException(
            "Failed to create a Map from data. Bad json in options column?", e);
      }
    }
    return null;
  }

  public static class SharedSecretSeriesMapperFactory {
    private final ObjectMapper mapper;
    private final GroupDAO.GroupDAOFactory groupDAOFactory;

    @Inject
    public SharedSecretSeriesMapperFactory(
        ObjectMapper mapper,
        GroupDAO.GroupDAOFactory groupDAOFactory) {
      this.mapper = mapper;
      this.groupDAOFactory = groupDAOFactory;
    }

    public SharedSecretSeriesMapper using(DSLContext context) {
      return new SharedSecretSeriesMapper(
          mapper,
          groupDAOFactory.using(context));
    }
  }
}
