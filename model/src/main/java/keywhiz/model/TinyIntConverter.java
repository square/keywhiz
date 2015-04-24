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

import org.jooq.Converter;

public class TinyIntConverter implements Converter<Byte, Boolean> {
  @Override public Boolean from(Byte aByte) {
    if (aByte == null) {
      return null;
    }

    return aByte == 1;
  }

  @Override public Byte to(Boolean aBoolean) {
    if (aBoolean == null) {
      return null;
    }

    return aBoolean ? (byte) 1 : 0;
  }

  @Override public Class<Byte> fromType() {
    return Byte.class;
  }

  @Override public Class<Boolean> toType() {
    return Boolean.class;
  }
}
