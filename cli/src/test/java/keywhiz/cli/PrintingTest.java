package keywhiz.cli;

import keywhiz.api.ApiDate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PrintingTest {
  @Test
  public void apiDateToString() {
    String iso8601 = "2024-03-03T00:00:00Z";
    ApiDate apiDate = ApiDate.parse(iso8601);
    assertEquals(iso8601, Printing.apiDateToString(apiDate));
  }

  @Test
  public void epochSecondToString() {
    assertEquals("2024-03-03T00:00:00Z", Printing.epochSecondToString(1709424000L));
  }
}
