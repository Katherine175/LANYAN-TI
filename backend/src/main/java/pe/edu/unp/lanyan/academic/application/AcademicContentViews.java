package pe.edu.unp.lanyan.academic.application;

import pe.edu.unp.lanyan.academic.api.AcademicContentDtos.*;
import pe.edu.unp.lanyan.academic.domain.*;

final class AcademicContentViews {
    private AcademicContentViews() { }
    static ScheduleView schedule(ClassSchedule s) {
        return new ScheduleView(s.getId(), AcademicViews.section(s.getSection()), s.getDayOfWeek(), s.getStartTime(),
                s.getEndTime(), "America/Lima", s.getModality(), s.getClassroom(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
    }
    static MaterialView material(CourseMaterial m) {
        return new MaterialView(m.getId(), AcademicViews.section(m.getSection()), m.getTitle(), m.getDescription(),
                m.getType(), m.getReferenceType(), m.getReference(), m.getStatus(),
                m.getCreatedAt(), m.getUpdatedAt(), m.getVersion());
    }
}
