package pe.edu.unp.lanyan;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.unp.lanyan.catalog.application.BusinessHours;
import static org.assertj.core.api.Assertions.*;

/** Horario de atención decidido por el Instituto: lunes a sábado de 7:00 a 21:00, hora de Lima. */
class BusinessHoursTest {
    static final ZoneId LIMA = ZoneId.of("America/Lima");
    final BusinessHours hours = new BusinessHours("America/Lima", "07:00", "21:00",
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY));

    static Instant lima(String localDateTime) { return LocalDateTime.parse(localDateTime).atZone(LIMA).toInstant(); }

    @ParameterizedTest(name = "{0} + {1} min = {2}")
    @CsvSource({
            // dentro del horario
            "2026-11-09T10:00, 30, 2026-11-09T10:30",
            // cruza el cierre: los minutos que faltan corren desde la apertura del día siguiente
            "2026-11-09T20:50, 30, 2026-11-10T07:20",
            // antes de la apertura: el reloj empieza a las 7:00
            "2026-11-09T05:00, 15, 2026-11-09T07:15",
            // sábado por la noche: el domingo no cuenta
            "2026-11-14T20:30, 60, 2026-11-16T07:30",
            // domingo: todo pasa al lunes
            "2026-11-15T12:00, 10, 2026-11-16T07:10",
            // justo al cierre
            "2026-11-09T21:00, 10, 2026-11-10T07:10",
            // 8 horas de atención desde la tarde
            "2026-11-09T18:00, 480, 2026-11-10T12:00",
    })
    void addsOnlyBusinessMinutes(String start, long minutes, String expected) {
        assertThat(hours.plusBusinessMinutes(lima(start), minutes)).isEqualTo(lima(expected));
    }

    @Test
    void countsOnlyTimeInsideOpeningHours() {
        // Sábado 20:00 a lunes 8:00: una hora el sábado y una el lunes.
        assertThat(hours.businessSecondsBetween(lima("2026-11-14T20:00"), lima("2026-11-16T08:00"))).isEqualTo(7200);
        assertThat(hours.businessSecondsBetween(lima("2026-11-09T10:00"), lima("2026-11-09T10:45"))).isEqualTo(2700);
        assertThat(hours.businessSecondsBetween(lima("2026-11-09T10:00"), lima("2026-11-09T09:00"))).isZero();
    }

    @Test
    void addingAndCountingAgree() {
        var start = lima("2026-11-13T19:37");
        var due = hours.plusBusinessMinutes(start, 240);
        assertThat(hours.businessSecondsBetween(start, due)).isEqualTo(240 * 60);
    }

    @Test
    void invalidScheduleIsRejected() {
        assertThatThrownBy(() -> new BusinessHours("America/Lima", "21:00", "07:00", EnumSet.of(DayOfWeek.MONDAY)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
