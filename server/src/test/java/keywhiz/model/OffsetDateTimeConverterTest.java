package keywhiz.model;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OffsetDateTimeConverterTest {

  OffsetDateTimeConverter converter;

  @Before public void setUp() {
    converter = new OffsetDateTimeConverter();
  }

  @Test public void testFrom() {
    assertThat(converter.from(Timestamp.valueOf("2015-6-2 13:2:34.9823")))
        .isEqualTo(OffsetDateTime.of(2015, 6, 2, 13, 2, 34, 982300000, ZoneOffset.UTC));
  }

  @Test public void testTo() {
    assertThat(converter.to(OffsetDateTime.of(1999, 12, 31, 6, 23, 59, 1265, ZoneOffset.UTC)))
        .isEqualTo(Timestamp.valueOf("1999-12-31 6:23:59.000001265"));
  }
}