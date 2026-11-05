package pl.tfhub.api.athlete.dto.request;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Year;

public class BirthYearValidator implements ConstraintValidator<ValidBirthYear, Integer> {

	@Override
	public boolean isValid(Integer birthYear, ConstraintValidatorContext context) {
		return birthYear == null || birthYear <= Year.now().getValue();
	}
}
