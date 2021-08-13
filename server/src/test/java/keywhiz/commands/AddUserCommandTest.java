package keywhiz.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Bootstrap;
import javax.validation.Validation;
import javax.validation.Validator;
import keywhiz.KeywhizConfig;
import keywhiz.KeywhizService;
import keywhiz.KeywhizTestRunner;
import keywhiz.test.ServiceContext;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class AddUserCommandTest {
  @Test
  public void injectsUserDAO() {
    ServiceContext context = ServiceContext.create();
    assertNotNull(AddUserCommand.getUserDAO(context.getBootstrap(), context.getConfig()));
  }
}
