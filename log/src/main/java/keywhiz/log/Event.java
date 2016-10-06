package keywhiz.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * A class to represent an event in Keywhiz
 */
public class Event {
  private final Instant timestamp;
  private final EventTag type;
  private final String user;
  private final String objectName; // The name of the affected object
  private final Map<String, String> extraInfo; // Any extra information

  private final DateTimeFormatter df =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

  public Event(Instant timestamp, EventTag type, String user, String objectName,
      Map<String, String> extraInfo) {
    this.timestamp = timestamp;
    this.type = type;
    this.user = user;
    this.objectName = objectName;
    this.extraInfo = extraInfo;
  }

  public Event(Instant timestamp, EventTag type, String user, String objectName) {
    this.timestamp = timestamp;
    this.type = type;
    this.user = user;
    this.objectName = objectName;
    this.extraInfo = new HashMap<>();
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public EventTag getType() {
    return type;
  }

  public String getUser() {
    return user;
  }

  public String getObjectName() {
    return objectName;
  }

  public Map<String, String> getExtraInfo() {
    return extraInfo;
  }

  @Override public String toString() {
    return new StringBuilder().append(type)
        .append(" Affected object: \"")
        .append(objectName)
        .append("\" Timestamp: \"")
        .append(df.format(timestamp))
        .append("\" User: \"")
        .append(user)
        .append("\" Additional information: \"")
        .append(extraInfo)
        .append("\"")
        .toString();
  }
}
