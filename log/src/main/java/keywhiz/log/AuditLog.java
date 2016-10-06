package keywhiz.log;

/**
 * An interface for recording Keywhiz events
 */
public interface AuditLog {
  void recordEvent(Event e);
}

