package keywhiz.api.model;

import org.junit.Test;

import static keywhiz.testing.JsonHelpers.asJson;
import static keywhiz.testing.JsonHelpers.fromJson;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretRetrievalCursorTest {
  @Test
  public void roundTripSerialization() throws Exception {
    SecretRetrievalCursor cursor = SecretRetrievalCursor.of("test-secret", 1234567);
    assertThat(fromJson(asJson(cursor), SecretRetrievalCursor.class)).isEqualTo(cursor);
  }

  @Test
  public void deserializesCorrectly() throws Exception {
    SecretRetrievalCursor cursor = SecretRetrievalCursor.of("test-secret", 1234567);
    assertThat(fromJson("{\"name\":\"test-secret\",\"expiry\":1234567}",
        SecretRetrievalCursor.class)).isEqualTo(cursor);
  }

  @Test
  public void roundTripUrlEncoded() throws Exception {
    SecretRetrievalCursor cursor = SecretRetrievalCursor.of("test_secret-123.crt", 1234567);
    SecretRetrievalCursor roundtripped =
        SecretRetrievalCursor.fromUrlEncodedString(
            SecretRetrievalCursor.toUrlEncodedString(cursor));

    assertThat(roundtripped).isEqualTo(cursor);
  }
}
