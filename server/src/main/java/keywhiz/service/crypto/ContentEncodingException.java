package keywhiz.service.crypto;

/**
 * An exception to be thrown when the ContentCryptographer fails
 */
public class ContentEncodingException extends RuntimeException {
  public ContentEncodingException(String msg) {
    super(msg);
  }
}
