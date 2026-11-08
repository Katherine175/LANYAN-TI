package pe.edu.unp.lanyan.academic.application;

import pe.edu.unp.lanyan.academic.api.AcademicDtos.*;
import pe.edu.unp.lanyan.academic.domain.*;

final class AcademicViews {
    private AcademicViews() { }
    static CourseView course(Course course) {
        return new CourseView(course.getId(), course.getCode(), course.getName(), course.getLanguage(),
                course.getLevel(), course.getDescription(), course.getStatus(), course.getCreatedAt());
    }
    static SectionView section(CourseSection section) {
        return new SectionView(section.getId(), section.getCourse().getId(), section.getCourse().getCode(),
                section.getCode(), section.getAcademicPeriod(), section.getStartDate(), section.getEndDate(),
                section.getCapacity(), section.getStatus(), section.getCreatedAt());
    }
    static EnrollmentView enrollment(Enrollment enrollment) {
        return new EnrollmentView(enrollment.getId(), enrollment.getStudent().getId(), enrollment.getStudent().getStudentCode(),
                enrollment.getSection().getId(), enrollment.getSection().getCourse().getId(), enrollment.getStatus(), enrollment.getEnrolledAt());
    }
    static StudentCourseView studentCourse(Enrollment enrollment) {
        return new StudentCourseView(enrollment.getId(), enrollment.getStatus(), enrollment.getEnrolledAt(),
                course(enrollment.getSection().getCourse()), section(enrollment.getSection()));
    }
}
