package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.List;

@Parameters(commandDescription = "Update a secret in KeyWhiz")
public class UpdateActionConfig extends AddOrUpdateActionConfig {
  @Parameter(description = "<secret>")
  public List<String> updateType;

  @Parameter(names = "--content", description = "Provide flag if secret content will be provided")
  public boolean contentProvided;
}
