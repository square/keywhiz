package keywhiz.service.daos;

import com.google.common.collect.ImmutableMap;
import java.util.Random;
import java.util.UUID;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.service.config.Readwrite;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(KeywhizTestRunner.class)
public class SecretSeriesMapperTest {
  @Inject private SecretSeriesMapper mapper;
  @Inject @Readwrite private GroupDAO groupDAO;

  @Test
  public void mapsRecordWithNullOwner() {
    SecretsRecord record = minimalRecord();
    assertNull(record.getOwner());

    SecretSeries series = mapper.map(record);
    assertNull(series.owner());
  }

  @Test
  public void mapsRecordWithExistingOwner() {
    String ownerName = randomName();
    Long ownerId = createGroup(ownerName);

    SecretsRecord record = minimalRecord();
    record.setOwner(ownerId);

    SecretSeries series = mapper.map(record);
    assertEquals(ownerName, series.owner());
  }

  @Test
  public void mapsRecordWithNonExistingOwner() {
    SecretsRecord record = minimalRecord();
    record.setOwner(randomLong());

    assertThrows(IllegalStateException.class, () -> mapper.map(record));
  }

  private Long createGroup(String ownerName) {
    return groupDAO.createGroup(ownerName, null , null, ImmutableMap.of());
  }

  private SecretsRecord minimalRecord() {
    SecretsRecord record = new SecretsRecord();

    record.setCreatedat(randomLong());
    record.setId(randomLong());
    record.setName(randomName());
    record.setOptions("");
    record.setUpdatedat(randomLong());

    return record;
  }

  private static long randomLong() {
    return new Random().nextInt(Integer.MAX_VALUE);
  }

  private static String randomName() {
    return UUID.randomUUID().toString();
  }
}
