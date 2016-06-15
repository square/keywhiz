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

package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.util.List;

@Parameters(commandDescription = "Create or update secret.")
public class CreateOrUpdateActionConfig {
  @Parameter(names = "--secret", description = "Name of the secret to add", required = true)
  public String secretName;

  @Parameter(names = "--json", description = "Metadata JSON blob")
  public String json;

  @Parameter(names = { "-e", "--expiry" }, description = "Secret expiry. For keystores, it is recommended to use the expiry of the earliest key. Format should be 2006-01-02T15:04:05Z or seconds since epoch.")
  public String expiry;
}
