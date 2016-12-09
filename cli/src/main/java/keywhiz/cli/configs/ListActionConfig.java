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

@Parameters(commandDescription = "List groups, clients, or secrets.")
public class ListActionConfig {

  @Parameter(description = "[<groups|clients|secrets>]")
  public List<String> listType;

  @Parameter(names = "--idx", description = "Index to start retrieving secrets (valid only with 'secrets'; requires --num to also be specified)")
  public Integer idx;

  @Parameter(names = "--num", description = "Number of secrets to retrieve after index (valid only with 'secrets'; requires --idx to also be specified)")
  public Integer num;

  @Parameter(names = "--newestFirst", description = "Whether to batch the secrets from newest creation date first.  Defaults to 'true' (valid only with 'secrets'; requires --idx and --num to also be specified)")
  public Boolean newestFirst;
}
