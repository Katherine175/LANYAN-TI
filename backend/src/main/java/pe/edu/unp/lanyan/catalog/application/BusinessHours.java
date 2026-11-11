package pe.edu.unp.lanyan.catalog.application;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Horario de atención del Instituto: el reloj del SLA solo corre dentro de él.
 * Por defecto, lunes a sábado de 7:00 a 21:00, hora de Lima. No considera feriados.
 */
@Component
public class BusinessHours {
    private final ZoneId zone;
    private final LocalTime opens;
    private final LocalTime closes;
    private final Set<DayOfWeek> days;

    public BusinessHours(@Value("${lanyan.sla.zone:America/Lima}") String zone,
            @Value("${lanyan.sla.opens:07:00}") String opens,
            @Value("${lanyan.sla.closes:21:00}") String closes,
            @Value("${lanyan.sla.days:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY}") Set<DayOfWeek> days) {
        this.zone = ZoneId.of(zone);
        this.opens = LocalTime.parse(opens);
        this.closes = LocalTime.parse(closes);
        this.days = days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days);
        if (!this.closes.isAfter(this.opens) || this.days.isEmpty()) {
            throw new IllegalArgumentException("Horario de atención inválido.");
        }
    }

    /** Momento en que se cumplen {@code minutes} de atención contados desde {@code start}. */
    public Instant plusBusinessMinutes(Instant start, long minutes) {
        long remaining = Duration.ofMinutes(minutes).toSeconds();
        var t = alignToOpen(start.atZone(zone));
        while (true) {
            long available = Duration.between(t, closingOf(t)).toSeconds();
            if (remaining <= available) return t.plusSeconds(remaining).toInstant();
            remaining -= available;
            t = alignToOpen(closingOf(t));
        }
    }

    /** Segundos de atención transcurridos entre dos momentos (0 si {@code to} es anterior). */
    public long businessSecondsBetween(Instant from, Instant to) {
        if (!to.isAfter(from)) return 0;
        long total = 0;
        var t = alignToOpen(from.atZone(zone));
        var end = to.atZone(zone);
        while (t.isBefore(end)) {
            var close = closingOf(t);
            total += Duration.between(t, close.isBefore(end) ? close : end).toSeconds();
            t = alignToOpen(close);
        }
        return total;
    }

    private ZonedDateTime closingOf(ZonedDateTime t) { return t.toLocalDate().atTime(closes).atZone(zone); }

    /** El primer momento de atención igual o posterior a {@code t}. */
    private ZonedDateTime alignToOpen(ZonedDateTime t) {
        var current = t;
        for (int i = 0; i < 15; i++) {
            var time = current.toLocalTime();
            if (days.contains(current.getDayOfWeek()) && time.isBefore(closes)) {
                return time.isBefore(opens) ? current.toLocalDate().atTime(opens).atZone(zone) : current;
            }
            current = current.toLocalDate().plusDays(1).atTime(opens).atZone(zone);
        }
        throw new IllegalStateException("No hay días de atención configurados.");
    }
}
