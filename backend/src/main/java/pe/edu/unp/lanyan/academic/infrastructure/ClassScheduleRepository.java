package pe.edu.unp.lanyan.academic.infrastructure;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import pe.edu.unp.lanyan.academic.domain.ClassSchedule;

public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, UUID> {
    @EntityGraph(attributePaths = {"section", "section.course"})
    Optional<ClassSchedule> findByIdAndDeletedAtIsNull(UUID id);
    @EntityGraph(attributePaths = {"section", "section.course"})
    Page<ClassSchedule> findBySection_IdAndDeletedAtIsNull(UUID sectionId, Pageable pageable);

    @Query("select s from ClassSchedule s where s.deletedAt is null and s.section.id in :sectionIds")
    java.util.List<ClassSchedule> findActiveBySectionIds(java.util.Collection<UUID> sectionIds);

    @Query("select s.section.id from ClassSchedule s where s.id=:id and s.deletedAt is null")
    Optional<UUID> findActiveSectionId(UUID id);

    @EntityGraph(attributePaths = {"section", "section.course"})
    @Query("""
        select s from ClassSchedule s where s.deletedAt is null and s.section.status <> 'CANCELLED'
        and exists (select e.id from Enrollment e where e.section=s.section
            and e.student.id=:profileId and e.status='ENROLLED')
        """)
    Page<ClassSchedule> findForStudent(UUID profileId, Pageable pageable);
}
