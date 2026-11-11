package pe.edu.unp.lanyan;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.domain.*;
import pe.edu.unp.lanyan.academic.api.AcademicContentDtos.*;
import pe.edu.unp.lanyan.academic.application.*;
import pe.edu.unp.lanyan.academic.domain.*;
import pe.edu.unp.lanyan.academic.infrastructure.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AcademicContentRulesTest {
    ClassScheduleRepository schedules = mock(ClassScheduleRepository.class);
    CourseMaterialRepository materials = mock(CourseMaterialRepository.class);
    CourseSectionRepository sections = mock(CourseSectionRepository.class);
    StudentProfileRepository profiles = mock(StudentProfileRepository.class);
    ClassScheduleService scheduleService = new ClassScheduleService(schedules,sections,profiles);
    CourseMaterialService materialService = new CourseMaterialService(materials,sections,profiles);
    CourseSection section;

    @BeforeEach
    void fixture() {
        var course=new Course("TEST","Curso ficticio","Idioma ficticio",null,null,null);
        section=new CourseSection(course,"A","TEST",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),20,null);
        when(sections.findForAcademicContent(section.getId())).thenReturn(Optional.of(section));
        when(schedules.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        when(materials.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    }

    ScheduleWriteRequest schedule() {
        return new ScheduleWriteRequest(DayOfWeek.MONDAY,LocalTime.of(8,0),LocalTime.of(9,0),ClassModality.IN_PERSON," Aula A ",null);
    }
    MaterialWriteRequest material() {
        return new MaterialWriteRequest(" Guia ",null,MaterialType.GUIDE,MaterialReferenceType.LOCAL_REFERENCE,"Cursos/Guia.pdf",null,null);
    }

    @Test
    void defaultsAndNormalizationAreSafe() {
        var s=scheduleService.create(section.getId(),schedule());
        assertThat(s.section().id()).isEqualTo(section.getId());
        assertThat(s.timeZone()).isEqualTo("America/Lima");
        assertThat(s.classroom()).isEqualTo("Aula A");
        var m=materialService.create(section.getId(),material());
        assertThat(m.title()).isEqualTo("Guia");
        assertThat(m.status()).isEqualTo(MaterialStatus.DRAFT);
        assertThat(m.reference()).isEqualTo("cursos/guia.pdf");
    }

    @ParameterizedTest
    @CsvSource({"09:00,09:00","10:00,09:00","23:00,01:00","08:00:01,09:00","08:00,09:00:01","08:00:00.000000001,09:00"})
    void invalidTimeIntervalsAreRejected(String start,String end) {
        assertThatThrownBy(()->new ClassSchedule(section,DayOfWeek.MONDAY,LocalTime.parse(start),LocalTime.parse(end),ClassModality.ONLINE,null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dayModalityAndRoomAreValidated() {
        for (var modality:List.of(ClassModality.IN_PERSON,ClassModality.HYBRID))
            assertThatThrownBy(()->new ClassSchedule(section,DayOfWeek.MONDAY,LocalTime.of(8,0),LocalTime.of(9,0),modality," "))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ClassSchedule(section,null,LocalTime.of(8,0),LocalTime.of(9,0),ClassModality.ONLINE,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ClassSchedule(section,DayOfWeek.MONDAY,LocalTime.of(8,0),LocalTime.of(9,0),null,"A")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ClassSchedule(section,DayOfWeek.SUNDAY,LocalTime.of(8,0),LocalTime.of(9,0),ClassModality.ONLINE,null).getClassroom()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={"../secret","a/../secret","/absolute","C:/secret.pdf","a//b","a/%2e%2e/b","a/","a b.pdf","a\\b","a/./b","a..pdf","https://example.org/a"})
    void unsafeLocalReferencesAreRejected(String reference) {
        assertThatThrownBy(()->MaterialReferences.normalize(MaterialReferenceType.LOCAL_REFERENCE,reference)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings={"http://example.org/a","javascript:alert(1)","file:///C:/a","data:text/plain,a","https://localhost/a","https://127.0.0.1/a","https://[::1]/a","https://example.local/a","https://host.internal/a","https://a.localhost/a","https://user:pass@example.org/a","https://example.org:8443/a","https://example.org/%0a","https://example.org/a b","https://example.org./a","https://-host.example.org/a","https://example.org\\evil/a","https:///a"})
    void unsafeHttpsReferencesAreRejected(String reference) {
        assertThatThrownBy(()->MaterialReferences.normalize(MaterialReferenceType.HTTPS_URL,reference)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void httpsReferencesAreCanonicalizedWithoutNetworkAccess() {
        assertThat(MaterialReferences.normalize(MaterialReferenceType.HTTPS_URL,"HTTPS://EXAMPLE.ORG:443/a/../guide.pdf?q=one%20two#page=1"))
                .isEqualTo("https://example.org/guide.pdf?q=one%20two#page=1");
        assertThat(MaterialReferences.normalize(MaterialReferenceType.HTTPS_URL,"https://example.org")).isEqualTo("https://example.org/");
        assertThatThrownBy(()->MaterialReferences.normalize(MaterialReferenceType.LOCAL_REFERENCE,"a".repeat(1025))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MaterialReferences.normalize(MaterialReferenceType.HTTPS_URL,"https://example.org/"+"a".repeat(2048))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linksRequireHttpsAndTitlesAreMandatory() {
        assertThatThrownBy(()->new CourseMaterial(section,"A",null,MaterialType.LINK,MaterialReferenceType.LOCAL_REFERENCE,"guide.pdf",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CourseMaterial(section," ",null,MaterialType.GUIDE,MaterialReferenceType.LOCAL_REFERENCE,"guide.pdf",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CourseMaterial(section,"A",null,null,MaterialReferenceType.LOCAL_REFERENCE,"guide.pdf",null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlapsAndDuplicatesAreRejectedWithoutSaving() {
        var existing=new ClassSchedule(section,schedule().dayOfWeek(),schedule().startTime(),schedule().endTime(),schedule().modality(),schedule().classroom());
        when(schedules.findActiveBySectionIds(java.util.List.of(section.getId()))).thenReturn(java.util.List.of(existing));
        assertThat(catchThrowableOfType(()->scheduleService.create(section.getId(),schedule()),AcademicException.class).code()).isEqualTo("SCHEDULE_OVERLAP");
        verify(schedules,never()).saveAndFlush(any());
        when(materials.duplicate(eq(section.getId()),any(),anyString(),any())).thenReturn(true);
        assertThat(catchThrowableOfType(()->materialService.create(section.getId(),material()),AcademicException.class).code()).isEqualTo("MATERIAL_EXISTS");
        verify(materials,never()).saveAndFlush(any());
    }

    @Test
    void missingSectionProfileAndResourceAreReported() {
        assertThat(catchThrowableOfType(()->scheduleService.create(UUID.randomUUID(),schedule()),AcademicException.class).code()).isEqualTo("SECTION_NOT_FOUND");
        assertThat(catchThrowableOfType(()->materialService.get(UUID.randomUUID()),AcademicException.class).code()).isEqualTo("MATERIAL_NOT_FOUND");
        assertThat(catchThrowableOfType(()->scheduleService.get(UUID.randomUUID()),AcademicException.class).code()).isEqualTo("SCHEDULE_NOT_FOUND");
        assertThatThrownBy(()->scheduleService.mySchedule(UUID.randomUUID(),0,20)).isInstanceOf(StudentProfileNotFoundException.class);
        assertThatThrownBy(()->materialService.myMaterials(UUID.randomUUID(),0,20)).isInstanceOf(StudentProfileNotFoundException.class);
    }

    @Test
    void paginationIsValidatedBeforeDatabaseAccess() {
        assertThatThrownBy(()->scheduleService.mySchedule(UUID.randomUUID(),-1,20)).isInstanceOf(AcademicException.class);
        assertThatThrownBy(()->materialService.list(section.getId(),0,101)).isInstanceOf(AcademicException.class);
        verifyNoInteractions(profiles,schedules,materials,sections);
    }

    @Test
    void staleAndMissingVersionsCannotMutateContent() {
        var s=mock(ClassSchedule.class);
        when(schedules.findActiveSectionId(any())).thenReturn(Optional.of(section.getId()));
        when(schedules.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(s));
        when(s.getVersion()).thenReturn(2L);
        assertThat(catchThrowableOfType(()->scheduleService.delete(UUID.randomUUID(),null),AcademicException.class).code()).isEqualTo("VERSION_REQUIRED");
        assertThat(catchThrowableOfType(()->scheduleService.delete(UUID.randomUUID(),1L),AcademicException.class).code()).isEqualTo("STALE_RESOURCE");
        verify(s,never()).retire();
        var m=mock(CourseMaterial.class);
        when(materials.findActiveSectionId(any())).thenReturn(Optional.of(section.getId()));
        when(materials.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(m));
        when(m.getVersion()).thenReturn(2L);
        assertThat(catchThrowableOfType(()->materialService.delete(UUID.randomUUID(),null),AcademicException.class).code()).isEqualTo("VERSION_REQUIRED");
        assertThat(catchThrowableOfType(()->materialService.delete(UUID.randomUUID(),1L),AcademicException.class).code()).isEqualTo("STALE_RESOURCE");
        verify(m,never()).retire();
    }

    @Test
    void deletionRetainsBothDomainRecordsAndTheirSection() {
        var s=new ClassSchedule(section,DayOfWeek.MONDAY,LocalTime.of(8,0),LocalTime.of(9,0),ClassModality.ONLINE,null);
        var m=new CourseMaterial(section,"Guia",null,MaterialType.GUIDE,MaterialReferenceType.LOCAL_REFERENCE,"a.pdf",null);
        s.retire(); m.retire();
        assertThat(s.getDeletedAt()).isNotNull();
        assertThat(m.getDeletedAt()).isNotNull();
        assertThat(s.getSection()).isSameAs(section);
        assertThat(m.getSection()).isSameAs(section);
    }
}
