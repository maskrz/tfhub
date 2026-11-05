package pl.tfhub.api.validation;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class StrictBooleanDeserializer extends ValueDeserializer<Boolean> {

	@Override
	public Boolean deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.hasToken(JsonToken.VALUE_TRUE)) {
			return true;
		}
		if (parser.hasToken(JsonToken.VALUE_FALSE)) {
			return false;
		}
		return context.reportInputMismatch(Boolean.class, "Value must be a boolean");
	}
}
