package keywhiz.service.permissions;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class HelloWorldAnnotationImplementation implements MethodInterceptor {
  public Object invoke(MethodInvocation invocation) throws Throwable {
     System.out.print("Hello World");
     return invocation.proceed();
  }
}
