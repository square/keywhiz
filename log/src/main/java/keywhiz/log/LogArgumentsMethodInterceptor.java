package keywhiz.log;

import java.lang.reflect.Parameter;
import java.util.AbstractMap;
import java.util.Arrays;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogArgumentsMethodInterceptor implements MethodInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(LogArguments.class);

  public Object invoke(MethodInvocation invocation) throws Throwable {
    Parameter[] parameters = invocation.getMethod().getParameters();
    Object[] arguments = invocation.getArguments();

    Object[] parameterArgumentPairs = new Object[arguments.length];

    for (int i = 0; i < arguments.length; i++) {
      if (parameters[i].isNamePresent()) {
        parameterArgumentPairs[i] = new AbstractMap.SimpleImmutableEntry<>(parameters[i].getName(), arguments[i]);
      } else {
        parameterArgumentPairs[i] = arguments[i];
      }
    }

    logger.info(String.format("Method Name: %s, Method Arguments: %s",
            invocation.getMethod().getName(),
            Arrays.deepToString(parameterArgumentPairs)));

    return invocation.proceed();
  }
}
