package pe.edu.unp.lanyan.academic.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.academic.api.CatalogDtos.CatalogCourseView;
import pe.edu.unp.lanyan.academic.api.CatalogDtos.CatalogScheduleView;
import pe.edu.unp.lanyan.academic.api.CatalogDtos.CatalogSectionView;
import pe.edu.unp.lanyan.academic.domain.ClassSchedule;
import pe.edu.unp.lanyan.academic.domain.CourseSection;
import pe.edu.unp.lanyan.academic.domain.CourseStatus;
import pe.edu.unp.lanyan.academic.domain.SectionStatus;
import pe.edu.unp.lanyan.academic.infrastructure.ClassScheduleRepository;
import pe.edu.unp.lanyan.academic.infrastructure.CourseRepository;
import pe.edu.unp.lanyan.academic.infrastructure.CourseSectionRepository;
import pe.edu.unp.lanyan.academic.infrastructure.EnrollmentRepository;

/** Oferta vigente: cursos activos con sus secciones abiertas, cupos libres y horarios. */
@Service
@Transactional(readOnly = true)
public class CourseCatalogService {
    private final CourseRepository courses;
    private final CourseSectionRepository sections;
    private final ClassScheduleRepository schedules;
    private final EnrollmentRepository enrollments;

    public CourseCatalogService(CourseRepository courses, CourseSectionRepository sections,
            ClassScheduleRepository schedules, EnrollmentRepository enrollments) {
        this.courses = courses;
        this.sections = sections;
        this.schedules = schedules;
        this.enrollments = enrollments;
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN') and hasAuthority('academic:catalog:read')")
    public List<CatalogCourseView> catalog() {
        var activeCourses = courses.findByStatus(CourseStatus.ACTIVE, Sort.by("language", "name", "code"));
        var openSections = sections.findByCourse_StatusAndStatus(CourseStatus.ACTIVE, SectionStatus.OPEN);
        var sectionIds = openSections.stream().map(CourseSection::getId).toList();

        Map<UUID, Long> enrolled = new HashMap<>();
        Map<UUID, List<ClassSchedule>> schedulesBySection = Map.of();
        if (!sectionIds.isEmpty()) {
            enrollments.countEnrolledBySection(sectionIds).forEach(row -> enrolled.put((UUID) row[0], (Long) row[1]));
            schedulesBySection = schedules.findActiveBySectionIds(sectionIds).stream()
                    .collect(Collectors.groupingBy(schedule -> schedule.getSection().getId()));
        }

        var weekOrder = Comparator.comparing(ClassSchedule::getDayOfWeek).thenComparing(ClassSchedule::getStartTime);
        var sectionOrder = Comparator.comparing(CourseSection::getStartDate).thenComparing(CourseSection::getCode);
        var byCourse = openSections.stream().collect(Collectors.groupingBy(section -> section.getCourse().getId()));
        var finalSchedules = schedulesBySection;

        return activeCourses.stream().map(course -> new CatalogCourseView(course.getId(), course.getCode(), course.getName(),
                course.getLanguage(), course.getLevel(), course.getDescription(),
                byCourse.getOrDefault(course.getId(), List.of()).stream().sorted(sectionOrder).map(section -> {
                    long taken = enrolled.getOrDefault(section.getId(), 0L);
                    var classTimes = finalSchedules.getOrDefault(section.getId(), List.of()).stream().sorted(weekOrder)
                            .map(s -> new CatalogScheduleView(s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getModality(), s.getClassroom()))
                            .toList();
                    return new CatalogSectionView(section.getId(), section.getCode(), section.getAcademicPeriod(),
                            section.getStartDate(), section.getEndDate(), section.getCapacity(),
                            (int) Math.max(0, section.getCapacity() - taken), classTimes);
                }).toList())).toList();
    }
}
