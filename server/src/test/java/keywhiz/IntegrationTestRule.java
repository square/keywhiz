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

package keywhiz;

import com.google.common.io.Resources;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.rules.RuleChain;

public class IntegrationTestRule {
  public IntegrationTestRule() {}

  public static RuleChain rule() {
    String configPath = Resources.getResource("keywhiz-test.yaml").getPath();
    return RuleChain
        .outerRule(new MigrationsRule())
        .around(new DropwizardAppRule<>(KeywhizService.class, configPath));
  }
}
