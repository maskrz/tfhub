package pl.tfhub.api.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetAccountPasswordRequest(@NotBlank @Size(max = 1024) String newPassword) {
}
