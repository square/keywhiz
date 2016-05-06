/*
 * Copyright (C) 2016 Square, Inc.
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

package keywhiz;

import com.codahale.metrics.health.HealthCheck;
import java.io.Serializable;

/**  Returns unhealthy when the ManualStatus servlet gets called */
public class ManualStatusHealthCheck extends HealthCheck implements Serializable {
  private boolean healthy = true;

  void setHealthy(boolean healthy) {
    this.healthy = healthy;
  }

  @Override protected Result check() throws Exception {
    if(this.healthy) {
      return Result.healthy();
    }
    return Result.unhealthy("Server set unhealthy");
  }
}
