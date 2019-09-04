package keywhiz.api.model;

import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretRetrievalCursorTest {
  @Test
  public void serializesCorrectly() throws Exception {
    SecretRetrievalCursor cursor = SecretRetrievalCursor.of("test-secret", 1234567);
    assertThat(asJson(cursor))
        .isEqualTo("{\"name\":\"test-secret\",\"expiry\":1234567}");
  }

  @Test
  public void roundTrip() throws Exception {
    SecretRetrievalCursor cursor = SecretRetrievalCursor.of("test_secret-123.crt", 1234567);
    SecretRetrievalCursor roundtripped =
        SecretRetrievalCursor.fromUrlEncodedString(
            SecretRetrievalCursor.toUrlEncodedString(cursor));

    assertThat(roundtripped).isEqualTo(cursor);
  }
}
