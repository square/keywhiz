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

import com.google.common.primitives.UnsignedLongs;
import java.time.OffsetDateTime;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionGeneratorTest {
  @Test
  public void versionStampIncreases() throws Exception {
    long currentTime = System.currentTimeMillis();
    VersionGenerator stamp = new VersionGenerator(currentTime);
    VersionGenerator nextStamp = new VersionGenerator(currentTime + 1);
    assertThat(UnsignedLongs.compare(stamp.toLong(), nextStamp.toLong())).isLessThan(0);
    assertThat(stamp.toHex().compareTo(nextStamp.toHex())).isLessThan(0);
  }

  @Test
  public void versionStampDoesNotRollover() { // for a long time at least
    VersionGenerator currentStamp = new VersionGenerator(System.currentTimeMillis());
    VersionGenerator futureStamp = new VersionGenerator(
        OffsetDateTime.parse("2078-01-01T00:00Z").toInstant().toEpochMilli());
    assertThat(UnsignedLongs.compare(currentStamp.toLong(), futureStamp.toLong())).isLessThan(0);
  }

  @Test
  public void versionStampHexDoesNotRollover() { // for a long time at least
    VersionGenerator currentStamp = new VersionGenerator(System.currentTimeMillis());
    VersionGenerator futureStamp = new VersionGenerator(
        OffsetDateTime.parse("2078-01-01T00:00Z").toInstant().toEpochMilli());
    assertThat(currentStamp.toHex().compareTo(futureStamp.toHex())).isLessThan(0);
  }

  @Test
  public void versionStampFromLong() {
    VersionGenerator stamp1 = VersionGenerator.now();
    VersionGenerator stamp2 = VersionGenerator.fromLong(stamp1.toLong());
    assertThat(stamp1).isEqualTo(stamp2);
  }
}
