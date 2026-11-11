package pe.edu.unp.lanyan;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.edu.unp.lanyan.operations.application.OperationsService;
import static org.assertj.core.api.Assertions.*;

/** Disponibilidad de 30 días = (tiempo total - tiempo no disponible) / tiempo total. */
class AvailabilityMathTest {
    static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    static final Instant WINDOW_START = NOW.minus(Duration.ofDays(30));
    static final Instant LONG_AGO = Instant.parse("2025-01-01T00:00:00Z");
    static final BigDecimal TARGET = new BigDecimal("99.50");

    static Object[] change(String from, String to, Duration ago) { return new Object[] {from, to, NOW.minus(ago)}; }

    @Test
    void aTwoHourOutageInThirtyDaysGivesNinetyNinePointSeventyTwo() {
        var view = OperationsService.measure(UUID.randomUUID(), "Plataforma virtual", "OPERATIONAL", TARGET, LONG_AGO, List.<Object[]>of(
                change("OPERATIONAL", "UNAVAILABLE", Duration.ofHours(10)),
                change("UNAVAILABLE", "OPERATIONAL", Duration.ofHours(8))), WINDOW_START, NOW);
        // 30 días = 43 200 minutos; 120 sin servicio → 43 080 / 43 200 = 99,72 %.
        assertThat(view.availability()).isEqualByComparingTo("99.72");
        assertThat(view.meetsTarget()).isTrue();
        assertThat(view.outages()).isEqualTo(1);
        assertThat(view.unavailableMinutes()).isEqualTo(120);
        assertThat(view.mtrsMinutes()).isEqualTo(120);
    }

    @Test
    void anOngoingOutageCountsUntilNowAndHasNoRestoreTimeYet() {
        var view = OperationsService.measure(UUID.randomUUID(), "Internet", "UNAVAILABLE", TARGET, LONG_AGO, List.<Object[]>of(
                change("OPERATIONAL", "UNAVAILABLE", Duration.ofHours(1))), WINDOW_START, NOW);
        assertThat(view.unavailableMinutes()).isEqualTo(60);
        assertThat(view.mtrsMinutes()).isNull();
        assertThat(view.outages()).isEqualTo(1);
    }

    @Test
    void degradedTimeIsTrackedButStillCountsAsAvailable() {
        var view = OperationsService.measure(UUID.randomUUID(), "Correo", "OPERATIONAL", TARGET, LONG_AGO, List.<Object[]>of(
                change("OPERATIONAL", "DEGRADED", Duration.ofHours(5)),
                change("DEGRADED", "OPERATIONAL", Duration.ofHours(2))), WINDOW_START, NOW);
        assertThat(view.availability()).isEqualByComparingTo("100.00");
        assertThat(view.degradedMinutes()).isEqualTo(180);
        assertThat(view.outages()).isZero();
    }

    @Test
    void anOutageThatStartedBeforeTheWindowOnlyCountsInsideIt() {
        var view = OperationsService.measure(UUID.randomUUID(), "Pagos", "OPERATIONAL", TARGET, LONG_AGO, List.<Object[]>of(
                change("OPERATIONAL", "UNAVAILABLE", Duration.ofDays(31)),
                change("UNAVAILABLE", "OPERATIONAL", Duration.ofDays(30).minusHours(3))), WINDOW_START, NOW);
        assertThat(view.unavailableMinutes()).isEqualTo(180);
        assertThat(view.outages()).isZero();
        // 180 / 43 200 sin servicio → 99,58 %, sobre la meta de 99,50 %.
        assertThat(view.availability()).isEqualByComparingTo("99.58");
    }

    @Test
    void aServiceWithoutHistoryIsFullyAvailableAndANewServiceIsMeasuredFromItsCreation() {
        var full = OperationsService.measure(UUID.randomUUID(), "Proyectores", "OPERATIONAL", TARGET, LONG_AGO, List.<Object[]>of(), WINDOW_START, NOW);
        assertThat(full.availability()).isEqualByComparingTo("100.00");
        var recent = OperationsService.measure(UUID.randomUUID(), "Nuevo", "OPERATIONAL", TARGET, NOW.minus(Duration.ofHours(10)), List.<Object[]>of(
                change("OPERATIONAL", "UNAVAILABLE", Duration.ofHours(1))), WINDOW_START, NOW);
        assertThat(recent.availability()).isEqualByComparingTo("90.00");
        assertThat(recent.meetsTarget()).isFalse();
    }
}
