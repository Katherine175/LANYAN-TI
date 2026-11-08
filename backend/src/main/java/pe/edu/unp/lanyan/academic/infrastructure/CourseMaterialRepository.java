package pe.edu.unp.lanyan.academic.infrastructure;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import pe.edu.unp.lanyan.academic.domain.*;

public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, UUID> {
    @EntityGraph(attributePaths = {"section", "section.course"})
    Optional<CourseMaterial> findByIdAndDeletedAtIsNull(UUID id);
    @EntityGraph(attributePaths = {"section", "section.course"})
    Page<CourseMaterial> findBySection_IdAndDeletedAtIsNull(UUID sectionId, Pageable pageable);

    @Query("select m.section.id from CourseMaterial m where m.id=:id and m.deletedAt is null")
    Optional<UUID> findActiveSectionId(UUID id);

    @Query("""
        select (count(m) > 0) from CourseMaterial m where m.section.id=:sectionId
        and m.deletedAt is null and m.referenceType=:referenceType and m.reference=:reference and m.id<>:excludedId
        """)
    boolean duplicate(UUID sectionId, MaterialReferenceType referenceType, String reference, UUID excludedId);

    @EntityGraph(attributePaths = {"section", "section.course"})
    @Query("""
        select m from CourseMaterial m where m.deletedAt is null and m.status='PUBLISHED'
        and m.section.status <> 'CANCELLED'
        and exists (select e.id from Enrollment e where e.section=m.section
            and e.student.id=:profileId and e.status='ENROLLED')
        """)
    Page<CourseMaterial> findForStudent(UUID profileId, Pageable pageable);
}
