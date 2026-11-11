package pe.edu.unp.lanyan.catalog.domain;

import java.time.Instant;
import jakarta.persistence.*;

/** Tiempos objetivo de una prioridad, en minutos de horario de atención. */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {
    @Id @Enumerated(EnumType.STRING) @Column(length = 16) private ServicePriority priority;
    @Column(name = "first_response_minutes", nullable = false) private int firstResponseMinutes;
    @Column(name = "resolution_minutes", nullable = false) private int resolutionMinutes;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected SlaPolicy() { }

    public void revise(int firstResponseMinutes, int resolutionMinutes) {
        if (firstResponseMinutes < 1 || resolutionMinutes < firstResponseMinutes) {
            throw new IllegalArgumentException("La resolución no puede ser menor que la primera respuesta.");
        }
        this.firstResponseMinutes = firstResponseMinutes;
        this.resolutionMinutes = resolutionMinutes;
        this.updatedAt = Instant.now();
    }

    public ServicePriority getPriority() { return priority; }
    public int getFirstResponseMinutes() { return firstResponseMinutes; }
    public int getResolutionMinutes() { return resolutionMinutes; }
    public Long getVersion() { return version; }
}
