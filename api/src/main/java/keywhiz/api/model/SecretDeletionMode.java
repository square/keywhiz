package keywhiz.api.model;

import com.google.common.collect.ImmutableList;
import java.util.List;

public final class SecretDeletionMode {
  private SecretDeletionMode() {}

  public static final String HARD = "hard";
  public static final String SOFT = "soft";

  private static final List<String> values = ImmutableList.of(HARD, SOFT);

  public static List<String> values() {
    return values;
  }
}
