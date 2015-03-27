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

package keywhiz.auth;

import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SubtlesTest {
  @Test
  public void itReturnsFalseWhenBytesDontMatch() throws Exception {
    byte[] a = Hex.decodeHex("000A00BB".toCharArray());
    byte[] b = Hex.decodeHex("000A01BB".toCharArray());
    assertThat(Subtles.secureCompare(a, b)).isFalse();
  }

  @Test
  public void itReturnsFalseWithBytesOfDifferentLengths() throws Exception {
    byte[] a = Hex.decodeHex("000A00BB".toCharArray());
    byte[] b = Hex.decodeHex("000A00".toCharArray());
    assertThat(Subtles.secureCompare(a, b)).isFalse();
  }

  @Test
  public void itReturnsTrueWhenBytesMatch() throws Exception {
    byte[] a = Hex.decodeHex("030A01BF".toCharArray());
    byte[] b = Hex.decodeHex("030A01BF".toCharArray());
    assertThat(Subtles.secureCompare(a, b)).isTrue();
  }
}
