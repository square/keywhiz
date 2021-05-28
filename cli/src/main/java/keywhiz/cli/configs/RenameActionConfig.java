package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Rename a secret")
public class RenameActionConfig {

  @Parameter(description = "<new name>", required = true)
  public String newName;

  @Parameter(names = "--name", description = "Name of the secret to rename")
  public String secretName;

  @Parameter(names = "--id", description = "ID of the secret to rename")
  public Long secretId;
}
