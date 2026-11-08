package pe.edu.unp.lanyan.academic.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.*;
import pe.edu.unp.lanyan.academic.domain.*;
import pe.edu.unp.lanyan.academic.infrastructure.*;

@Service
@Transactional(readOnly = true)
public class CourseSectionService {
    private final CourseRepository courses;
    private final CourseSectionRepository sections;
    public CourseSectionService(CourseRepository courses, CourseSectionRepository sections) {
        this.courses = courses; this.sections = sections;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:sections:manage')")
    public SectionView create(UUID courseId, CreateSectionRequest request) {
        var course = courses.findById(courseId).orElseThrow(() ->
                new AcademicException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Curso no encontrado."));
        var section = new CourseSection(course, request.code(), request.academicPeriod(), request.startDate(),
                request.endDate(), request.capacity(), request.status());
        if (sections.existsByCourse_IdAndAcademicPeriodAndCode(courseId, section.getAcademicPeriod(), section.getCode())) {
            throw new AcademicException(HttpStatus.CONFLICT, "SECTION_EXISTS", "Ya existe esa sección del curso en el periodo indicado.");
        }
        return AcademicViews.section(sections.saveAndFlush(section));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public PageView<SectionView> list(UUID courseId, int page, int size) {
        var pagination = AcademicPages.request(page, size, "createdAt");
        if (!courses.existsById(courseId)) throw new AcademicException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Curso no encontrado.");
        return PageView.of(sections.findByCourse_Id(courseId, pagination).map(AcademicViews::section));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public SectionView get(UUID id) {
        return AcademicViews.section(sections.findById(id).orElseThrow(() ->
                new AcademicException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Sección no encontrada.")));
    }
}
