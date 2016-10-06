package keywhiz.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A very simple logger which logs all events passed to it
 */
public class SimpleLogger implements AuditLog {
  private static final Logger logger = LoggerFactory.getLogger(SimpleLogger.class);

  @Override public void recordEvent(Event e) {
    logger.info(e.toString());
  }
}
