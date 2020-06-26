package keywhiz.service.providers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Structured representation of Envoy's {@code X-Forwarded-Client-Cert} (XFCC) header.
 *
 * @see <a href="https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_conn_man/headers#x-forwarded-client-cert">Envoy
 * XFCC spec</a>
 */
public class XfccHeader {
  /**
   * Elements contained in this header.
   */
  public final Element[] elements;

  /**
   * Element of the XFCC header. Contains information about one certificate as a list of key-value
   * pairs.
   */
  public static class Element {
    /**
     * Key-value pairs contained in this header element.
     */
    public final Pair[] pairs;

    /**
     * Constructs a new instance of {@code Element} with the provided key-value pairs.
     */
    public Element(Pair... pairs) {
      this.pairs = pairs;
    }

    @Override
    public String toString() {
      return "Element{" + Arrays.toString(pairs) + "}";
    }

    /**
     * Key-value pair contained in the XFCC header element.
     */
    public static class Pair {
      /**
       * Name/key of the pair.
       */
      public final String key;

      /**
       * Value of the pair.
       */
      public final String value;

      /**
       * Constructs a new {@code Pair} with the specified key and value.
       */
      public Pair(String key, String value) {
        this.key = key;
        this.value = value;
      }

      @Override
      public String toString() {
        return "(" + key + ", " + value + ")";
      }
    }
  }

  /**
   * Constructs a new instance of {@code XfccHeader} with the provided elements.
   */
  public XfccHeader(Element... elements) {
    this.elements = elements;
  }

  @Override
  public String toString() {
    return "XfccHeader{" + Arrays.toString(elements) + "}";
  }

  /**
   * Parses the provided XFCC header value and returns its contents in a structured form.
   *
   * @throws ParseException if an error occurred while parsing the header.
   */
  public static XfccHeader parse(String headerValue) throws ParseException {
    //   All rules:
    // HEADER  := ELEMENT [',' ELEMENT]*
    // ELEMENT := [PAIR [';' PAIR]]*
    // PAIR    := KEY '=' VALUE
    // KEY     := [^=;,"]*
    // VALUE   := QUOTED_VALUE | UNQUOTED_VALUE
    // QUOTED_VALUE   := '"' [^"]* '"'
    // UNQUOTED_VALUE := [^=;,"]*
    //    Double-quotes in values should be replaced by \"
    List<Element> elements = new ArrayList<>();
    int pos = 0;
    pos = parseElement(headerValue, pos, elements);
    while (pos < headerValue.length()) {
      char c = headerValue.charAt(pos);
      if (c != ',') {
        throw new ParseException("Invalid character '" + c
            + "' instead of element delimiter at position " + pos);
      }
      pos++;
      pos = parseElement(headerValue, pos, elements);
    }
    return new XfccHeader(elements.toArray(new Element[elements.size()]));
  }

  /**
   * Consumes a single element located at the provided position in the header.
   *
   * @param headerValue XFCC header contents to parse.
   * @param pos         {@code 0}-based offset at which to start parsing.
   * @param elements    List into which to add the resulting parsed element.
   * @return {@code 0}-based offset in the header at which this element ends. The offset is one past
   * the last character of the element.
   */
  private static int parseElement(String headerValue, int pos, List<Element> elements)
      throws ParseException {
    //   Rules relevant to this method:
    // HEADER  := ELEMENT [',' ELEMENT]*
    // ELEMENT := [PAIR [';' PAIR]]
    List<Element.Pair> pairs = new ArrayList<>();
    while (pos < headerValue.length()) {
      char c = headerValue.charAt(pos);
      if (c == ',') {
        // End of element
        break;
      }
      if ((!pairs.isEmpty()) && (c == ';')) {
        // Consume pair delimiter
        pos++;
      }
      pos = parsePair(headerValue, pos, pairs);
    }

    elements.add(new Element(pairs.toArray(new Element.Pair[pairs.size()])));
    return pos;
  }

  /**
   * Consumes a single key-value pair located at the provided position in the header.
   *
   * @param headerValue XFCC header contents to parse.
   * @param pos         {@code 0}-based offset at which to start parsing.
   * @param pairs       List into which to add the resulting parsed pair.
   * @return {@code 0}-based offset in the header at which this pair ends. The offset is one past
   * the last character of the pair.
   */
  private static int parsePair(String headerValue, int pos, List<Element.Pair> pairs)
      throws ParseException {
    //   Rules relevant to this method:
    // ELEMENT := [PAIR [';' PAIR]]
    // PAIR    := KEY '=' VALUE
    // KEY     := [^;,="]*
    // VALUE   := QUOTED_VALUE | UNQUOTED_VALUE
    // QUOTED_VALUE   := '"' [^"]* '"'
    // UNQUOTED_VALUE := [^;,="]*
    //    Double-quotes in values should be replaced by \"

    int keyStartPosition = pos;

    // Consume key and the terminating =
    String key;
    while (true) {
      if (pos >= headerValue.length()) {
        throw new ParseException("Unterminated key starts at position " + keyStartPosition
            + ", current position: " + pos);
      }
      char c = headerValue.charAt(pos);
      if (c == '=') {
        // End of key
        key = headerValue.substring(keyStartPosition, pos);
        pos++;
        break;
      }

      if ((c == ',') || (c == ';') || (c == '"')) {
        // The "spec" is vague about whether these characters are permitted in a key. Err
        // on the side of rejecting.
        throw new ParseException("Invalid character '" + c
            + "' inside key which starts at position " + keyStartPosition);
      }

      pos++;
    }

    // Consume value
    if (pos > headerValue.length()) {
      throw new ParseException("Missing value for key which starts at position "
          + keyStartPosition + ", current position: " + pos);
    }
    StringBuilder value = new StringBuilder();
    if (pos < headerValue.length()) {
      if (headerValue.charAt(pos) == '"') {
        // Consume quoted value
        pos = parseQuotedValue(headerValue, pos, value);
      } else {
        // Consume unquoted value
        pos = parseUnquotedValue(headerValue, pos, value);
      }
    }

    pairs.add(new Element.Pair(key, value.toString()));
    return pos;
  }

  /**
   * Consumes a single unquoted value of a key-value pair, located at the provided position in the
   * header.
   *
   * @param headerValue XFCC header contents to parse.
   * @param pos         {@code 0}-based offset at which to start parsing.
   * @param value       {@code StringBuilder} into which to output the value.
   * @return {@code 0}-based offset in the header at which this value ends. The offset is one past
   * the last character of the value.
   */
  private static int parseUnquotedValue(String headerValue, int pos, StringBuilder value)
      throws ParseException {
    // UNQUOTED_VALUE := [^;,="]*
    //     Double-quotes in values should be replaced by \"
    int startPosition = pos;
    while (pos < headerValue.length()) {
      char c = headerValue.charAt(pos);

      if ((c == ';') || (c == ',')) {
        // End of key-value pair
        break;
      } else if (c == '"') {
        if ((pos == 0) || (headerValue.charAt(pos - 1) != '\\')) {
          throw new ParseException("Invalid character '" + c
              + "' inside unquoted value which starts at position " + startPosition
              + ", current position: " + pos);
        } else {
          // Escaped double quote
          value.setCharAt(value.length() - 1, '"');
          pos++;
          continue;
        }
      } else if (c == '=') {
        throw new ParseException("Invalid character '" + c
            + "' inside unquoted value which starts at position " + startPosition
            + ", current position: " + pos);
      }

      // Consume valid character
      value.append(c);
      pos++;
    }
    return pos;
  }

  /**
   * Consumes a single quoted value of a key-value pair, located at the provided position in the
   * header.
   *
   * @param headerValue XFCC header contents to parse.
   * @param pos         {@code 0}-based offset at which to start parsing.
   * @param value       {@code StringBuilder} into which to output the value.
   * @return {@code 0}-based offset in the header at which this value ends. The offset is one past
   * the last character (terminating quote) of the value.
   */
  private static int parseQuotedValue(String headerValue, int pos, StringBuilder value)
      throws ParseException {
    // QUOTED_VALUE := '"' [^"]* '"'
    //     Double-quotes in values should be replaced by \"
    if (pos >= headerValue.length()) {
      throw new ParseException("Missing value at position " + pos);
    }
    if (headerValue.charAt(pos) != '"') {
      throw new IllegalArgumentException("Not a quoted value");
    }
    int startPosition = pos;
    pos++;
    while (true) {
      if (pos >= headerValue.length()) {
        throw new ParseException("Unterminated quoted value starts at position "
            + startPosition + ", current position: " + pos);
      }
      char c = headerValue.charAt(pos);

      if (c == '"') {
        if (headerValue.charAt(pos - 1) != '\\') {
          // End of quoted value
          pos++;
          break;
        } else {
          // Escaped double quote
          value.setCharAt(value.length() - 1, '"');
          pos++;
          continue;
        }
      }

      // Consume valid character
      value.append(c);
      pos++;
    }
    return pos;
  }

  /**
   * Indicates that an error occurred during the parsing of an XFCC header.
   */
  public static class ParseException extends Exception {
    private static final long serialVersionUID = 1;

    /**
     * Constructs a new instance of {@code ParseException} with the specified message.
     */
    public ParseException(String message) {
      super(message);
    }

    /**
     * Constructs a new instance of {@code ParseException} with the specified message and cause.
     */
    public ParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
