/* Author: BigDL
 * Date: 12/5/2022
*/

package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.List;

@Parameters(commandDescription = "Get secret from KeyWhiz server")
public class GetActionConfig {
    @Parameter(names = "--name", description = "Name of the secret to get", required = true)
    public String name;
}
