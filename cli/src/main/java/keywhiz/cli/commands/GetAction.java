/* Author: BigDL
 * Date: 12/5/2022
*/

package keywhiz.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import keywhiz.api.SecretDetailResponse;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;
import keywhiz.cli.configs.AddActionConfig;
import keywhiz.cli.Printing;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import keywhiz.cli.configs.GetActionConfig;

import static java.lang.String.format;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class GetAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(GetAction.class);
  private final Printing printing;
  private final GetActionConfig config;
  private final KeywhizClient keywhizClient;

  InputStream stream = System.in;

  public GetAction(GetActionConfig config, KeywhizClient client, Printing printing) {
    this.config = config;
    this.keywhizClient = client;
    this.printing = printing;
  }

  @Override public void run() {

    String name = config.name;

    if (name == null || !validName(name)) {
      throw new IllegalArgumentException(format("Invalid secret name, must match %s", VALID_NAME_PATTERN));
    }
    try {
      String key = keywhizClient.getKeyByName(name);
      System.out.println(key);
    } catch (NotFoundException e) {
        throw new AssertionError("The specific secret does not exist.");
    } catch (IOException e) {
        throw Throwables.propagate(e);
    }
  }
}

