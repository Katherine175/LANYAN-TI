package pe.edu.unp.lanyan.catalog.api;

import pe.edu.unp.lanyan.catalog.domain.ServicePriority;
import pe.edu.unp.lanyan.catalog.domain.SlaPolicy;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import pe.edu.unp.lanyan.catalog.domain.ItService;
import pe.edu.unp.lanyan.catalog.domain.OperationalStatus;
import pe.edu.unp.lanyan.catalog.domain.ServiceCategory;
import pe.edu.unp.lanyan.catalog.domain.ServiceIcon;
import pe.edu.unp.lanyan.catalog.domain.ServiceLifecycle;

public final class CatalogDtos {
    private CatalogDtos() { }

    public record CategoryWriteRequest(@NotBlank @Size(max = 80) String name) { }

    public record ServiceWriteRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 1000) String description,
            @NotNull UUID categoryId, @NotBlank @Size(max = 120) String owner, @NotNull ServiceIcon icon, ServiceLifecycle lifecycleStatus,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal availabilityTarget,
            @Min(1) @Max(525600) Integer responseTargetMinutes, @PositiveOrZero Long version, ServicePriority defaultPriority) { }

    public record SlaPolicyView(ServicePriority priority, int firstResponseMinutes, int resolutionMinutes, Long version) {
        public static SlaPolicyView of(SlaPolicy policy) {
            return new SlaPolicyView(policy.getPriority(), policy.getFirstResponseMinutes(), policy.getResolutionMinutes(), policy.getVersion());
        }
    }

    public record SlaPolicyWriteRequest(@NotNull @Min(1) @Max(10080) Integer firstResponseMinutes,
            @NotNull @Min(1) @Max(43200) Integer resolutionMinutes, @NotNull @PositiveOrZero Long version) { }

    public record OperationalStatusRequest(@NotNull OperationalStatus status, @NotNull @PositiveOrZero Long version) { }

    public record CategoryView(UUID id, String name) {
        public static CategoryView of(ServiceCategory category) { return new CategoryView(category.getId(), category.getName()); }
    }

    public record ServiceView(UUID id, String code, String name, String description, CategoryView category, String owner,
            ServiceIcon icon, ServiceLifecycle lifecycleStatus, OperationalStatus operationalStatus,
            BigDecimal availabilityTarget, int responseTargetMinutes, ServicePriority defaultPriority, Long version) {
        public static ServiceView of(ItService s) {
            return new ServiceView(s.getId(), s.getCode(), s.getName(), s.getDescription(), CategoryView.of(s.getCategory()), s.getOwner(),
                    s.getIcon(), s.getLifecycleStatus(), s.getOperationalStatus(), s.getAvailabilityTarget(), s.getResponseTargetMinutes(), s.getDefaultPriority(), s.getVersion());
        }
    }
}
