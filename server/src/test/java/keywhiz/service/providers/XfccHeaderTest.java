package keywhiz.service.providers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link XfccHeader}
 */
public class XfccHeaderTest {

  /**
   * Tests the test vectors from Envoy's "spec".
   *
   * @see <a href="https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_conn_man/headers#x-forwarded-client-cert">Envoy
   * XFCC spec</a>
   */
  @Test
  public void testSpec() {
    assertParseSucceeds(
        "By=http://frontend.lyft.com;Hash=468ed33be74eee6556d90c0149c1309e9ba61d6425303443c0748a02dd8de688;Subject=\"/C=US/ST=CA/L=San Francisco/OU=Lyft/CN=Test Client\";URI=http://testclient.lyft.com",
        new XfccHeader(
            new XfccHeader.Element(
                new XfccHeader.Element.Pair("By", "http://frontend.lyft.com"),
                new XfccHeader.Element.Pair("Hash",
                    "468ed33be74eee6556d90c0149c1309e9ba61d6425303443c0748a02dd8de688"),
                new XfccHeader.Element.Pair("Subject",
                    "/C=US/ST=CA/L=San Francisco/OU=Lyft/CN=Test Client"),
                new XfccHeader.Element.Pair("URI", "http://testclient.lyft.com")
            )));
    assertParseSucceeds(
        "By=http://frontend.lyft.com;Hash=468ed33be74eee6556d90c0149c1309e9ba61d6425303443c0748a02dd8de688;URI=http://testclient.lyft.com,By=http://backend.lyft.com;Hash=9ba61d6425303443c0748a02dd8de688468ed33be74eee6556d90c0149c1309e;URI=http://frontend.lyft.com",
        new XfccHeader(
            new XfccHeader.Element(
                new XfccHeader.Element.Pair("By", "http://frontend.lyft.com"),
                new XfccHeader.Element.Pair("Hash",
                    "468ed33be74eee6556d90c0149c1309e9ba61d6425303443c0748a02dd8de688"),
                new XfccHeader.Element.Pair("URI", "http://testclient.lyft.com")
            ),
            new XfccHeader.Element(
                new XfccHeader.Element.Pair("By", "http://backend.lyft.com"),
                new XfccHeader.Element.Pair("Hash",
                    "9ba61d6425303443c0748a02dd8de688468ed33be74eee6556d90c0149c1309e"),
                new XfccHeader.Element.Pair("URI", "http://frontend.lyft.com")
            )));
    assertParseSucceeds(
        "By=http://frontend.lyft.com;Hash=468ed33be74eee6556d90c0149c1309e9ba61d6425303443c0748a02dd8de688;Subject=\"/C=US/ST=CA/L=San Francisco/OU=Lyft/CN=Test Client\";URI=http://testclient.lyft.com;DNS=lyft.com;DNS=www.lyft.com",
        new XfccHeader(
            new XfccHeader.Element(
                new XfccHeader.Element.Pair("By", "http://frontend.lyft.com"),
                new XfccHeader.Element.Pair("Hash",
                    "468ed33be74eee6556d90c0149c1309e9ba61d6425303443c0748a02dd8de688"),
                new XfccHeader.Element.Pair("Subject",
                    "/C=US/ST=CA/L=San Francisco/OU=Lyft/CN=Test Client"),
                new XfccHeader.Element.Pair("URI", "http://testclient.lyft.com"),
                new XfccHeader.Element.Pair("DNS", "lyft.com"),
                new XfccHeader.Element.Pair("DNS", "www.lyft.com")
            )));
  }

  @Test
  public void testEmptyElements() {
    assertParseSucceeds("", new XfccHeader(new XfccHeader.Element()));
    assertParseSucceeds(",", new XfccHeader(new XfccHeader.Element(), new XfccHeader.Element()));
    assertParseSucceeds(
        ",,",
        new XfccHeader(new XfccHeader.Element(), new XfccHeader.Element(),
            new XfccHeader.Element()));
  }

  @Test
  public void testEmptyPairs() {
    assertParseFails(";");
  }

  @Test
  public void testSpaces() {
    assertParseFails(" ");
    assertParseFails(", ");
    assertParseFails(" ,");
    assertParseFails(", ,");

    assertParseSucceeds(" a=b", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair(" a", "b"))));
    assertParseSucceeds("a =b", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("a ", "b"))));
    assertParseSucceeds("a= b", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("a", " b"))));
    assertParseSucceeds("a=b ", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("a", "b "))));
  }

  @Test
  public void testEmptyKeyOrValue() {
    assertParseSucceeds("=", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("", ""))));
    assertParseSucceeds("=\"\"", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("", ""))));
    assertParseSucceeds("a=", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("a", ""))));
    assertParseSucceeds("=b", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("", "b"))));
  }

  @Test
  public void testPairKeyInvalidCharacters() {
    assertParseFails("a\"=b");
    assertParseFails("a\\\"=");
    assertParseFails("a\"\"=b");
    assertParseFails("\"a\"=b");
    assertParseFails("a;=b");
    assertParseFails("a,=b");
  }

  @Test
  public void testPairValueInvalidCharacters() {
    assertParseFails("a=b\"");
    assertParseFails("a=b\"\"");
    assertParseFails("a=b;c");
    assertParseFails("a=b,c");
    assertParseFails("a=b=c");
  }

  @Test
  public void testPairValueQuotes() {
    assertParseSucceeds("a=\"\"", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("a", ""))));
    assertParseSucceeds("a=\"\\\",b=\\\"\"", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("a", "\",b=\""))));
    assertParseSucceeds("a=\",\"", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("a", ","))));
    assertParseSucceeds("a=\";\"", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("a", ";"))));
    assertParseSucceeds("a=\"=\"", new XfccHeader(new XfccHeader.Element(
        new XfccHeader.Element.Pair("a", "="))));
  }

  private static void assertParseFails(String headerValue) {
    XfccHeader header;
    try {
      header = XfccHeader.parse(headerValue);
    } catch (XfccHeader.ParseException e) {
      // expected
      return;
    }
    fail("Expected to fail, but got " + header);
  }

  private static void assertParseSucceeds(String actualHeaderValue, XfccHeader expectedResult) {
    XfccHeader actual;
    try {
      actual = XfccHeader.parse(actualHeaderValue);
    } catch (XfccHeader.ParseException e) {
      throw new AssertionError("Parsing failed instead of succeeding", e);
    }
    assertHeaderEquals(expectedResult, actual);
  }

  private static void assertHeaderEquals(XfccHeader expected, XfccHeader actual) {
    if (expected.elements.length != actual.elements.length) {
      fail("Expected<" + expected + ">, actual: <" + actual + ">. Different number of"
          + " elements. Expected:<" + expected.elements.length + ">, actual: <"
          + actual.elements.length + ">");
    }
    for (int i = 0; i < expected.elements.length; i++) {
      try {
        assertElementEquals(
            "Expected<" + expected + ">, actual: <" + actual + ">. Element #" + i
                + " differs",
            expected.elements[i],
            actual.elements[i]);
      } catch (AssertionError e) {
        fail("Expected<" + expected + ">, actual: <" + actual + ">. Element #" + i
            + " differs. " + e.getMessage());
      }
    }
  }

  private static void assertElementEquals(
      String message, XfccHeader.Element expected, XfccHeader.Element actual) {
    if (actual.pairs.length != expected.pairs.length) {
      fail(message + ": Different number of pairs. Expected <" + expected.pairs.length
          + ">, actual: <" + actual.pairs.length + ">");
    }
    for (int i = 0; i < expected.pairs.length; i++) {
      assertPairEquals(
          message + ": Pair #" + i + " differs", expected.pairs[i], actual.pairs[i]);
    }
  }

  private static void assertPairEquals(
      String message, XfccHeader.Element.Pair expected, XfccHeader.Element.Pair actual) {
    assertEquals(message + ": Key", expected.key, actual.key);
    assertEquals(message + ": Value", expected.value, actual.value);
  }
}
