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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OffsetDateTimeConverterTest {

  OffsetDateTimeConverter converter;

  @Before public void setUp() {
    converter = new OffsetDateTimeConverter();
  }

  @Test public void testFrom() {
    assertThat(converter.from(Timestamp.valueOf("2015-6-2 13:2:34.9823")))
        .isEqualTo(OffsetDateTime.of(2015, 6, 2, 13, 2, 34, 982300000, ZoneOffset.UTC));
  }

  @Test public void testTo() {
    assertThat(converter.to(OffsetDateTime.of(1999, 12, 31, 6, 23, 59, 1265, ZoneOffset.UTC)))
        .isEqualTo(Timestamp.valueOf("1999-12-31 6:23:59.000001265"));
  }
}
