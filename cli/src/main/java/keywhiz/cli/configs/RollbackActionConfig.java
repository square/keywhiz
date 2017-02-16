package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Rollback to a previous version of a secret")
public class RollbackActionConfig {

  @Parameter(names = "--name", description = "Name of the secret to roll back", required = true)
  public String name;

  @Parameter(names = "--version", description = "Version ID to roll back to (list versions to view IDs)", required = true)
  public Long id;
}
