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
import java.time.Instant;
import org.jooq.Converter;

/**
 * Converts SQL timestamps to long
 */
public class TimestampConverter implements Converter<Timestamp, Long> {
  @Override public Long from(Timestamp timestamp) {
    if (timestamp == null) {
      return 0L;
    }
    return timestamp.toInstant().getEpochSecond();
  }

  @Override public Timestamp to(Long value) {
    if (value == 0) {
      return null;
    }
    return Timestamp.from(Instant.ofEpochSecond(value));
  }

  @Override public Class<Timestamp> fromType() {
    return Timestamp.class;
  }

  @Override public Class<Long> toType() {
    return Long.class;
  }
}
