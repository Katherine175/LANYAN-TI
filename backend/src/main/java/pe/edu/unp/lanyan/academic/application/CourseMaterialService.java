package pe.edu.unp.lanyan.academic.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.academic.api.AcademicContentDtos.*;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.PageView;
import pe.edu.unp.lanyan.academic.domain.*;
import pe.edu.unp.lanyan.academic.infrastructure.*;

@Service
@Transactional(readOnly = true)
public class CourseMaterialService {
    private final CourseMaterialRepository materials;
    private final CourseSectionRepository sections;
    private final StudentProfileRepository profiles;
    public CourseMaterialService(CourseMaterialRepository materials, CourseSectionRepository sections, StudentProfileRepository profiles) {
        this.materials=materials; this.sections=sections; this.profiles=profiles;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:materials:manage')")
    public MaterialView create(UUID sectionId, MaterialWriteRequest request) {
        var section = lockSection(sectionId);
        var material = new CourseMaterial(section, request.title(), request.description(), request.type(),
                request.referenceType(), request.reference(), request.status());
        checkDuplicate(material);
        return AcademicContentViews.material(materials.saveAndFlush(material));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:materials:manage')")
    public MaterialView update(UUID id, MaterialWriteRequest request) {
        lockSection(materials.findActiveSectionId(id).orElseThrow(CourseMaterialService::notFound));
        var material = active(id);
        AcademicContentVersions.requireMatches(material.getVersion(),request.version());
        material.revise(request.title(), request.description(), request.type(), request.referenceType(), request.reference(), request.status());
        checkDuplicate(material);
        return AcademicContentViews.material(materials.saveAndFlush(material));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:materials:manage')")
    public void delete(UUID id, Long version) {
        lockSection(materials.findActiveSectionId(id).orElseThrow(CourseMaterialService::notFound));
        var material = active(id);
        AcademicContentVersions.requireMatches(material.getVersion(),version);
        material.retire();
        materials.flush();
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public MaterialView get(UUID id) { return AcademicContentViews.material(active(id)); }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public PageView<MaterialView> list(UUID sectionId, int page, int size) {
        var pagination = AcademicPages.request(page,size,"createdAt");
        if (!sections.existsById(sectionId)) throw sectionNotFound();
        return PageView.of(materials.findBySection_IdAndDeletedAtIsNull(sectionId,pagination).map(AcademicContentViews::material));
    }

    @PreAuthorize("hasRole('STUDENT') and hasAuthority('portal:academic:access') and hasAuthority('academic:materials:read:self') and #userId == authentication.principal.id")
    public PageView<MaterialView> myMaterials(UUID userId, int page, int size) {
        var pagination = AcademicPages.request(page,size,"createdAt");
        var profile = profiles.findByUser_Id(userId).orElseThrow(StudentProfileNotFoundException::new);
        return PageView.of(materials.findForStudent(profile.getId(),pagination).map(AcademicContentViews::material));
    }

    private void checkDuplicate(CourseMaterial m) {
        if (materials.duplicate(m.getSection().getId(),m.getReferenceType(),m.getReference(),m.getId()))
            throw new AcademicException(HttpStatus.CONFLICT,"MATERIAL_EXISTS","La referencia ya existe en esta seccion.");
    }
    private CourseSection lockSection(UUID id) {
        return sections.findForAcademicContent(id).orElseThrow(CourseMaterialService::sectionNotFound);
    }
    private CourseMaterial active(UUID id) {
        return materials.findByIdAndDeletedAtIsNull(id).orElseThrow(CourseMaterialService::notFound);
    }
    private static AcademicException notFound() {
        return new AcademicException(HttpStatus.NOT_FOUND,"MATERIAL_NOT_FOUND","Material no encontrado.");
    }
    private static AcademicException sectionNotFound() {
        return new AcademicException(HttpStatus.NOT_FOUND,"SECTION_NOT_FOUND","Seccion no encontrada.");
    }
}
