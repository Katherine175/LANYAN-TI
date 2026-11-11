package pe.edu.unp.lanyan.academic.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "academic_class_schedules")
public class ClassSchedule {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false) private CourseSection section;
    @Column(name = "day_of_week", nullable = false) private int dayOfWeekNumber;
    // Hora local de Lima tal cual: sin esto Hibernate la convierte a UTC y una clase de 18:00 a 20:00
    // se guarda como 23:00 a 01:00, que la base rechaza por terminar antes de empezar.
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "end_time", nullable = false) private LocalTime endTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ClassModality modality;
    @Column(length = 120) private String classroom;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private Long version;

    protected ClassSchedule() { }
    public ClassSchedule(CourseSection section, DayOfWeek day, LocalTime start, LocalTime end,
            ClassModality modality, String classroom) {
        this.id = UUID.randomUUID();
        this.section = Objects.requireNonNull(section);
        this.createdAt = Instant.now();
        revise(day, start, end, modality, classroom);
    }
    public void revise(DayOfWeek day, LocalTime start, LocalTime end, ClassModality modality, String classroom) {
        if (day == null || start == null || end == null || !end.isAfter(start)
                || start.getSecond() != 0 || end.getSecond() != 0 || start.getNano() != 0 || end.getNano() != 0
                || modality == null) throw new IllegalArgumentException("Horario invalido; utiliza horas y minutos del mismo dia.");
        String room = AcademicRules.optionalText(classroom, 120);
        if (modality != ClassModality.ONLINE && room == null) throw new IllegalArgumentException("El aula es obligatoria.");
        this.dayOfWeekNumber = day.getValue(); this.startTime = start; this.endTime = end;
        this.modality = modality; this.classroom = room; this.updatedAt = Instant.now();
    }
    public void retire() { this.deletedAt = Instant.now(); this.updatedAt = deletedAt; }
    public UUID getId() { return id; }
    public CourseSection getSection() { return section; }
    public DayOfWeek getDayOfWeek() { return DayOfWeek.of(dayOfWeekNumber); }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public ClassModality getModality() { return modality; }
    public String getClassroom() { return classroom; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Long getVersion() { return version; }
}
