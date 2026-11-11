package pe.edu.unp.lanyan.academic.api;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import pe.edu.unp.lanyan.academic.domain.ClassModality;

public final class CatalogDtos {
    private CatalogDtos() { }

    public record CatalogCourseView(UUID id, String code, String name, String language, String level,
            String description, List<CatalogSectionView> sections) { }

    public record CatalogSectionView(UUID id, String code, String academicPeriod, LocalDate startDate,
            LocalDate endDate, int capacity, int availableSeats, List<CatalogScheduleView> schedules) { }

    /** Horas de Lima, igual que el resto del módulo académico. */
    public record CatalogScheduleView(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
            ClassModality modality, String classroom) { }
}
