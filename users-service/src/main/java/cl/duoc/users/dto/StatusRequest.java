package cl.duoc.users.dto;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(
        @NotNull(message = "El campo active es obligatorio (true/false)")
        Boolean active
) {
}
