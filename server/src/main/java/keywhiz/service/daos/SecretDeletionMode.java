package keywhiz.service.daos;

import org.apache.commons.lang3.EnumUtils;

public enum SecretDeletionMode {
  HARD,
  SOFT;

  public static SecretDeletionMode valueOfIgnoreCase(String name) {
    SecretDeletionMode mode = EnumUtils.getEnumIgnoreCase(SecretDeletionMode.class, name);

    if (mode == null) {
      throw new IllegalArgumentException(
          String.format(
              "Unknown SecretDeletionMode value: %s. Valid values are: %",
              name,
              SecretDeletionMode.values()));
    }

    return mode;
  }
}
