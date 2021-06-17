package keywhiz.cli;

import static org.junit.Assert.assertEquals;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import keywhiz.cli.configs.RenameActionConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertTrue;

public class CliMainTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void commandsMapIncludesRename() {
    assertTrue(CliMain.CommandLineParsingContext.newCommandMap().containsKey("rename"));
  }

  @Test
  public void renameCommandMapsToRenameActionConfig() {
    assertTrue(CliMain.CommandLineParsingContext.newCommandMap().get("rename") instanceof RenameActionConfig);
  }

  @Test
  public void failsToParseRenameCommandWithoutResourceType() {
    CliMain.CommandLineParsingContext context = new CliMain.CommandLineParsingContext();
    JCommander commander = context.getCommander();

    thrown.expect(ParameterException.class);
    commander.parse("rename", "--oldName", "foo", "--newName", "bar");
  }

  @Test
  public void failsToParseRenameCommandWithoutOldName() {
    CliMain.CommandLineParsingContext context = new CliMain.CommandLineParsingContext();
    JCommander commander = context.getCommander();

    thrown.expect(ParameterException.class);
    commander.parse("rename", "secret", "--newName", "bar");
  }

  @Test
  public void failsToParseRenameCommandWithoutNewName() {
    CliMain.CommandLineParsingContext context = new CliMain.CommandLineParsingContext();
    JCommander commander = context.getCommander();

    thrown.expect(ParameterException.class);
    commander.parse("rename", "secret", "--oldName", "foo");
  }

  @Test
  public void parsesRenameCommand() {
    CliMain.CommandLineParsingContext context = new CliMain.CommandLineParsingContext();
    JCommander commander = context.getCommander();
    commander.parse("rename", "secret", "--oldName", "foo", "--newName", "bar");

    String parsedCommand = commander.getParsedCommand();
    assertEquals("rename", parsedCommand);

    RenameActionConfig config = (RenameActionConfig) context.getCommands().get(parsedCommand);
    assertEquals("secret", config.resourceType);
    assertEquals("foo", config.oldName);
    assertEquals("bar", config.newName);
  }
}
