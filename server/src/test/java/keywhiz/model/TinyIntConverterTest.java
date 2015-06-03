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

package keywhiz.model;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TinyIntConverterTest {
  TinyIntConverter converter;

  @Before
  public void setUp() {
    converter = new TinyIntConverter();
  }

  @Test public void fromTest() {
    assertThat(converter.from(null)).isNull();
    assertThat(converter.from((byte)0)).isFalse();
    assertThat(converter.from((byte)1)).isTrue();
    assertThat(converter.from((byte)2)).isTrue();
  }

  @Test public void toTest() {
    assertThat(converter.to(null)).isNull();
    assertThat(converter.to(true)).isEqualTo((byte) 1);
    assertThat(converter.to(false)).isEqualTo((byte)0);
  }
}