package keywhiz.api.model;

import com.google.common.collect.ImmutableMap;
import keywhiz.api.ApiDate;
import org.junit.Before;
import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static keywhiz.testing.JsonHelpers.jsonFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretSeriesTest {
  private SecretSeries series;

  @Before
  public void before() {
    ApiDate when = ApiDate.parse("2013-03-28T21:29:27.465Z");

    series = SecretSeries.builder()
        .createdAt(when)
        .createdBy("createdBy")
        .description("description")
        .generationOptions(ImmutableMap.of("foo", "bar"))
        .id(123)
        .name("name")
        .owner("owner")
        .updatedAt(when)
        .updatedBy("updatedBy")
        .build();
  }

  @Test public void roundTripSerialization() throws Exception {
    assertThat(fromJson(asJson(series), SecretSeries.class)).isEqualTo(series);
  }

  @Test public void deserializesCorrectly() throws Exception {
    assertThat(fromJson(jsonFixture("fixtures/model/secretSeries.json"), SecretSeries.class)).isEqualTo(series);
  }
}
