package keywhiz.service.exceptions;

import javax.ws.rs.WebApplicationException;

/**
 * HTTP exception indicating an unprocessable entity (code 422).
 */
public class UnprocessableEntityException extends WebApplicationException {
  public UnprocessableEntityException(String message) {
    super(message, 422);
  }
}
