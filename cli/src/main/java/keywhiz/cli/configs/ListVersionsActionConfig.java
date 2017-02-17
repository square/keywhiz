package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "List the previous versions of a secret")
public class ListVersionsActionConfig {

  @Parameter(names = "--name", description = "Name of the secret whose versions should be listed", required = true)
  public String name;

  @Parameter(names = "--idx", description = "Index of the first version to return in a list of versions sorted from newest to oldest update time")
  public int idx = 0;

  @Parameter(names = "--number", description = "Maximum number of versions to return in a list of versions sorted from newest to oldest update time")
  public int number = 10;
}