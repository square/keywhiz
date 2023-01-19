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

@Parameters(commandDescription = "Delete groups, clients, or secrets from KeyWhiz")
public class DeleteActionConfig {

  @Parameter(description = "<group|client|secret>")
  public List<String> deleteType;

  @Parameter(names = "--name", description = "Name of the item to delete", required = true)
  public String name;

  @Parameter(names = "--mode", description = "Deletion mode")
  public String mode;
}
