package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.List;

@Parameters(commandDescription = "Add clients, groups, or secrets to KeyWhiz")
public class AddActionConfig extends AddOrUpdateActionConfig {
  @Parameter(description = "<client|group|secret>")
  public List<String> addType;
}
