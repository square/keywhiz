package keywhiz.service.permission;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import keywhiz.service.permissions.HelloWorldModule;
import keywhiz.service.permissions.ClassContainsMethodWithHelloWorldAnnotation;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HelloWorldTest {

  @Test
  public void testPrintHelloWorld(){
    Injector injector = Guice.createInjector(new HelloWorldModule());
    ClassContainsMethodWithHelloWorldAnnotation
        testClass = injector.getInstance(ClassContainsMethodWithHelloWorldAnnotation.class);

    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputBytes));

    testClass.annotatedWithHelloWorld();

    String output = outputBytes.toString();

    assertThat(output).isEqualTo("Hello World");
  }
}
