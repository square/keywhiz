package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.List;

@Parameters(commandDescription = "Add clients, groups, or secrets to KeyWhiz. When adding a secret, " +
        "you will need to input the secret content if it is not already piped. " +
        "Use Ctrl+D to signal end of the input.")
public class AddActionConfig extends AddOrUpdateActionConfig {
  @Parameter(description = "<client|group|secret>")
  public List<String> addType;
}
