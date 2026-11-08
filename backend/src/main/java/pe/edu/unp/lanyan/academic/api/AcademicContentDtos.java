package pe.edu.unp.lanyan.academic.api;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;
import pe.edu.unp.lanyan.academic.domain.*;

public final class AcademicContentDtos {
    private AcademicContentDtos() { }

    public record ScheduleWriteRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime,
            @NotNull LocalTime endTime, @NotNull ClassModality modality, @Size(max=120) String classroom,
            @PositiveOrZero Long version) { }
    public record MaterialWriteRequest(@NotBlank @Size(max=160) String title, @Size(max=2000) String description,
            @NotNull MaterialType type, @NotNull MaterialReferenceType referenceType,
            @NotBlank @Size(max=2048) String reference, MaterialStatus status, @PositiveOrZero Long version) { }
    public record ScheduleView(UUID id, AcademicDtos.SectionView section, DayOfWeek dayOfWeek,
            LocalTime startTime, LocalTime endTime, String timeZone, ClassModality modality, String classroom,
            Instant createdAt, Instant updatedAt, Long version) { }
    public record MaterialView(UUID id, AcademicDtos.SectionView section, String title, String description,
            MaterialType type, MaterialReferenceType referenceType, String reference, MaterialStatus status,
            Instant createdAt, Instant updatedAt, Long version) { }
}
