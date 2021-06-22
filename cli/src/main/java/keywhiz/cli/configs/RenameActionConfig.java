package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Rename a secret")
public class RenameActionConfig {

  @Parameter(description = "<secret>", required = true)
  public String resourceType;

  @Parameter(names = "--oldName", description = "Resource to rename", required = true)
  public String oldName;

  @Parameter(names = "--newName", description = "New name", required = true)
  public String newName;
}
