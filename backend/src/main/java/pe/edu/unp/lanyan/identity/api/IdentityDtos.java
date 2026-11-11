package pe.edu.unp.lanyan.identity.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.unp.lanyan.identity.domain.RoleName;

public final class IdentityDtos {
    private IdentityDtos() { }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }

    public record CreateUserRequest(@NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotEmpty Set<@NotNull RoleName> roles) {
        @Override public String toString() { return "CreateUserRequest[redacted]"; }
    }

    /** password vacío o ausente conserva la contraseña actual. */
    public record UpdateUserRequest(@NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull Boolean active,
            @NotEmpty Set<@NotNull RoleName> roles,
            @Size(max = 72) String password) {
        @Override public String toString() { return "UpdateUserRequest[redacted]"; }
    }

    public record AssignRolesRequest(@NotEmpty Set<@NotNull RoleName> roles) { }
    public record UserView(UUID id, String fullName, String email, boolean active,
            Set<RoleName> roles, Set<String> permissions) { }
    public record CsrfView(String token, String headerName) { }
    public record RoleView(RoleName name, Set<String> permissions) { }
}
