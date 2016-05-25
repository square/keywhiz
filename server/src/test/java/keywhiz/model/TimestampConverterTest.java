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

import java.sql.Timestamp;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TimestampConverterTest {

  TimestampConverter converter;

  @Before public void setUp() {
    converter = new TimestampConverter();
  }

  @Test public void testFrom() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    assertThat(converter.from(Timestamp.valueOf("2015-6-2 13:2:34")))
        .isEqualTo(1433250154);
  }

  @Test public void testTo() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    assertThat(converter.to(946621439L))
        .isEqualTo(Timestamp.valueOf("1999-12-31 6:23:59"));
  }
}
