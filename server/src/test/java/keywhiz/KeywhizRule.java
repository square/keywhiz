package keywhiz;

import com.google.inject.Injector;
import keywhiz.test.KeywhizTests;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class KeywhizRule implements TestRule {
  private final Object test;

  public KeywhizRule(Object test) {
    this.test = test;
  }

  @Override public Statement apply(Statement statement, Description description) {
    Injector injector = KeywhizTests.createInjector();
    injector.injectMembers(test);
    return statement;
  }
}
