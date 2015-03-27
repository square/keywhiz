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
import org.jooq.Converter;

/**
 * Converts SQL timestamps to java OffsetDateTime objects in UTC.
 */
public class OffsetDateTimeConverter implements Converter<Timestamp, OffsetDateTime> {
  @Override public OffsetDateTime from(Timestamp timestamp) {
    return timestamp.toLocalDateTime().atOffset(ZoneOffset.UTC);
  }

  @Override public Timestamp to(OffsetDateTime offsetDateTime) {
    return Timestamp.valueOf(offsetDateTime.toLocalDateTime());
  }

  @Override public Class<Timestamp> fromType() {
    return Timestamp.class;
  }

  @Override public Class<OffsetDateTime> toType() {
    return OffsetDateTime.class;
  }
}
