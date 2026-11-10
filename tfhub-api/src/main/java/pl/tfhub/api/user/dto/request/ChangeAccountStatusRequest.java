package pl.tfhub.api.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChangeAccountStatusRequest(@NotNull Boolean active) {
}
