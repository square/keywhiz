package keywhiz.cli;

import static org.junit.Assert.assertEquals;

import com.beust.jcommander.JCommander;
import keywhiz.cli.configs.RenameActionConfig;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CliMainTest {
  @Test
  public void commandsMapIncludesRename() {
    assertTrue(CliMain.ParseContext.newCommandMap().containsKey("rename"));
  }

  @Test
  public void renameCommandMapsToRenameActionConfig() {
    assertTrue(CliMain.ParseContext.newCommandMap().get("rename") instanceof RenameActionConfig);
  }

  @Test
  public void parsesRenameCommandWithId() {
    CliMain.ParseContext context = new CliMain.ParseContext();
    JCommander commander = context.getCommander();
    commander.parse("rename", "--id", "1", "newName");

    String parsedCommand = commander.getParsedCommand();
    assertEquals("rename", parsedCommand);

    RenameActionConfig config = (RenameActionConfig) context.getCommands().get(parsedCommand);
    assertEquals(Long.valueOf(1L), config.secretId);
    assertEquals("newName", config.newName);
  }

  @Test
  public void parsesRenameCommandWithName() {
    CliMain.ParseContext context = new CliMain.ParseContext();
    JCommander commander = context.getCommander();
    commander.parse("rename", "--name", "foo", "newName");

    String parsedCommand = commander.getParsedCommand();
    assertEquals("rename", parsedCommand);

    RenameActionConfig config = (RenameActionConfig) context.getCommands().get(parsedCommand);
    assertEquals("foo", config.secretName);
    assertEquals("newName", config.newName);
  }
}
