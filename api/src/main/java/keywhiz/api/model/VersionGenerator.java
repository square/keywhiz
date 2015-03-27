/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keywhiz.api.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Generates an 8 byte version stamp. The version should be lexicographically increasing and unique.
 *
 * 8 bytes are used because 16 byte values might overflow in some languages. The front 41 bits are a
 * timestamp from a recent epoch. The following 23 bits are random. 41 bits in the timestamp sets
 * the overflow after 2078.
 */
public class VersionGenerator {
  public static final long EPOCH = 1262304000000L; // 01-01-2010

  private long version;

  public VersionGenerator(long now) {
    checkArgument(now > EPOCH);
    long time = now - EPOCH;
    Random rand = new Random();
    version = (time << 23) | (rand.nextLong() & 0x7FFFFF);
  }

  public String toHex() {
    return String.format("%016x", version);
  }

  public long toLong() {
    return version;
  }

  /**
   * @param version seconds from {@link EPOCH}.
   * @return VersionStamp from a long.
   */
  public static VersionGenerator fromLong(long version) {
    VersionGenerator stamp = VersionGenerator.now();
    stamp.version = version;
    return stamp;
  }

  /** @return VersionStamp of the current time. */
  public static VersionGenerator now() {
    return new VersionGenerator(System.currentTimeMillis());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(version);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof VersionGenerator) {
      VersionGenerator that = (VersionGenerator) o;
      if (Objects.equal(this.version, that.version)) return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("version", version).toString();
  }
}
