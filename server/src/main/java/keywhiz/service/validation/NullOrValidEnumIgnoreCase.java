package keywhiz.service.validation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ PARAMETER })
@Retention(RUNTIME)
@Constraint(validatedBy = NullOrValidEnumIgnoreCaseValidator.class)
public @interface NullOrValidEnumIgnoreCase {
  String message() default "keywhiz.service.validation.NullOrValidEnumIgnoreCase.message";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  Class<? extends Enum<?>> value();
}
