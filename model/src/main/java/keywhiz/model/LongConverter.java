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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jooq.Converter;

public class LongConverter implements Converter<Integer, Long> {
  @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "null is a valid DB value")
  @Override public Long from(Integer value) {
    if (value == null) {
      return null;
    }

    return (long)value;
  }

  @Override public Integer to(Long value) {
    if (value == null) {
      return null;
    }
    return Math.toIntExact(value);
  }

  @Override public Class<Integer> fromType() {
    return Integer.class;
  }

  @Override public Class<Long> toType() {
    return Long.class;
  }
}
