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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/**
 * Value type containing a secret series and associated list of contents
 */
@AutoValue
public abstract class SecretSeriesAndVersions {
  public static SecretSeriesAndVersions of(SecretSeries series, ImmutableList<SecretContent> content) {
    return new AutoValue_SecretSeriesAndVersions(series, content);
  }

  public abstract SecretSeries series();
  public abstract ImmutableList<SecretContent> content();
}
