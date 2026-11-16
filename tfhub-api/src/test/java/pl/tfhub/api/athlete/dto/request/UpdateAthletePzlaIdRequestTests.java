package pl.tfhub.api.athlete.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UpdateAthletePzlaIdRequestTests {

	private static Validator validator;
	private static jakarta.validation.ValidatorFactory validatorFactory;

	@BeforeAll
	static void createValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		validatorFactory.close();
	}

	@Test
	void acceptsAnOptionalDigitsOnlyIdentifierAndNormalizesWhitespace() {
		assertThat(validator.validate(new UpdateAthletePzlaIdRequest(null))).isEmpty();
		assertThat(new UpdateAthletePzlaIdRequest(" 123456 " ).pzlaId()).isEqualTo("123456");
		assertThat(validator.validate(new UpdateAthletePzlaIdRequest("123456"))).isEmpty();
	}

	@Test
	void rejectsNondigitAndOverlengthIdentifiers() {
		assertThat(validator.validate(new UpdateAthletePzlaIdRequest("12a34")))
				.extracting(violation -> violation.getPropertyPath().toString())
				.contains("pzlaId");
		assertThat(validator.validate(new UpdateAthletePzlaIdRequest("1".repeat(21))))
				.extracting(violation -> violation.getPropertyPath().toString())
				.contains("pzlaId");
	}
}
