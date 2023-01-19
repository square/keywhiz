package keywhiz.client;

import okhttp3.HttpUrl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeywhizClientTest {
  @Test
  public void httpUrlResolveVsBuild() {
    HttpUrl baseUrl = HttpUrl.parse("https://localhost:4444");
    long secretId = 123;

    HttpUrl viaResolve = baseUrl.resolve(String.format("/admin/secrets/%d", secretId));
    HttpUrl viaBuilder = baseUrl.newBuilder()
        .addPathSegment("admin")
        .addPathSegment("secrets")
        .addPathSegment(Long.toString(secretId))
        .build();

    assertEquals(viaResolve, viaBuilder);
  }
}
