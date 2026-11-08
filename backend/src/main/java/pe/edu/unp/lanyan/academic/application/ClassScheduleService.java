package pe.edu.unp.lanyan.academic.application;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.academic.api.AcademicContentDtos.*;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.academic.domain.*;
import pe.edu.unp.lanyan.academic.infrastructure.*;

@Service
@Transactional(readOnly = true)
public class ClassScheduleService {
    private final ClassScheduleRepository schedules;
    private final CourseSectionRepository sections;
    private final StudentProfileRepository profiles;
    public ClassScheduleService(ClassScheduleRepository schedules, CourseSectionRepository sections, StudentProfileRepository profiles) {
        this.schedules=schedules; this.sections=sections; this.profiles=profiles;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:schedules:manage')")
    public ScheduleView create(UUID sectionId, ScheduleWriteRequest request) {
        var section = lockSection(sectionId);
        var schedule = new ClassSchedule(section, request.dayOfWeek(), request.startTime(), request.endTime(),
                request.modality(), request.classroom());
        checkOverlap(schedule);
        return AcademicContentViews.schedule(schedules.saveAndFlush(schedule));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:schedules:manage')")
    public ScheduleView update(UUID id, ScheduleWriteRequest request) {
        lockSection(schedules.findActiveSectionId(id).orElseThrow(ClassScheduleService::notFound));
        var schedule = active(id);
        AcademicContentVersions.requireMatches(schedule.getVersion(), request.version());
        schedule.revise(request.dayOfWeek(), request.startTime(), request.endTime(), request.modality(), request.classroom());
        checkOverlap(schedule);
        return AcademicContentViews.schedule(schedules.saveAndFlush(schedule));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:schedules:manage')")
    public void delete(UUID id, Long version) {
        lockSection(schedules.findActiveSectionId(id).orElseThrow(ClassScheduleService::notFound));
        var schedule = active(id);
        AcademicContentVersions.requireMatches(schedule.getVersion(), version);
        schedule.retire();
        schedules.flush();
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public ScheduleView get(UUID id) { return AcademicContentViews.schedule(active(id)); }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public PageView<ScheduleView> list(UUID sectionId, int page, int size) {
        var pagination = pagination(page,size);
        if (!sections.existsById(sectionId)) throw sectionNotFound();
        return PageView.of(schedules.findBySection_IdAndDeletedAtIsNull(sectionId, pagination).map(AcademicContentViews::schedule));
    }

    @PreAuthorize("hasRole('STUDENT') and hasAuthority('portal:academic:access') and hasAuthority('academic:schedules:read:self') and #userId == authentication.principal.id")
    public PageView<ScheduleView> mySchedule(UUID userId, int page, int size) {
        var pagination = pagination(page,size);
        var profile = profiles.findByUser_Id(userId).orElseThrow(StudentProfileNotFoundException::new);
        return PageView.of(schedules.findForStudent(profile.getId(), pagination).map(AcademicContentViews::schedule));
    }

    private Pageable pagination(int page,int size) {
        return AcademicPages.request(page,size,"createdAt")
                .withSort(Sort.by("dayOfWeekNumber","startTime","id"));
    }
    private void checkOverlap(ClassSchedule s) {
        if (schedules.overlaps(s.getSection().getId(), s.getDayOfWeek().getValue(), s.getStartTime(), s.getEndTime(), s.getId()))
            throw new AcademicException(HttpStatus.CONFLICT, "SCHEDULE_OVERLAP", "Existe otro horario que se cruza en esta seccion.");
    }
    private CourseSection lockSection(UUID id) {
        return sections.findForAcademicContent(id).orElseThrow(ClassScheduleService::sectionNotFound);
    }
    private ClassSchedule active(UUID id) {
        return schedules.findByIdAndDeletedAtIsNull(id).orElseThrow(ClassScheduleService::notFound);
    }
    private static AcademicException notFound() {
        return new AcademicException(HttpStatus.NOT_FOUND,"SCHEDULE_NOT_FOUND","Horario no encontrado.");
    }
    private static AcademicException sectionNotFound() {
        return new AcademicException(HttpStatus.NOT_FOUND,"SECTION_NOT_FOUND","Seccion no encontrada.");
    }
}
