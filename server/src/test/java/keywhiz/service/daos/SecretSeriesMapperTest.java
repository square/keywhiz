package keywhiz.service.daos;

import java.util.Random;
import java.util.UUID;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNull;

@RunWith(KeywhizTestRunner.class)
public class SecretSeriesMapperTest {
  @Inject private SecretSeriesMapper mapper;

  @Test
  public void mapsRecord() {
    SecretsRecord record = minimalRecord();
    assertNull(record.getOwner());

    SecretSeries series = mapper.map(record);
    assertNull(series.owner());
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
