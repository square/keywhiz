package keywhiz.service.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.EnumUtils;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

public class NullOrValidEnumIgnoreCaseValidator implements ConstraintValidator<NullOrValidEnumIgnoreCase, String> {

  private Class<? extends Enum<?>> enumClass;

  @Override public void initialize(NullOrValidEnumIgnoreCase annotation) {
    this.enumClass = annotation.value();
  }

  @Override
  public boolean isValid(String s, ConstraintValidatorContext context) {
    if (s == null) {
      return true;
    }

    if (!EnumUtils.isValidEnumIgnoreCase((Class) enumClass, s)) {
      HibernateConstraintValidatorContext hibernateContext = context.unwrap(
          HibernateConstraintValidatorContext.class);

      hibernateContext.disableDefaultConstraintViolation();

      hibernateContext
          .addExpressionVariable("input", s)
          .addExpressionVariable("enumClassName", enumClass.getSimpleName())
          .buildConstraintViolationWithTemplate("${input} is not a valid value for enum ${enumClassName}")
          .addConstraintViolation();

      return false;
    }

    return true;
  }
}
