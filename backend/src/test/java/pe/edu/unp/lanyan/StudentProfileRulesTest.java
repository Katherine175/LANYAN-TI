package pe.edu.unp.lanyan;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.unp.lanyan.academic.application.StudentProfileNotFoundException;
import pe.edu.unp.lanyan.academic.application.StudentProfileService;
import pe.edu.unp.lanyan.academic.domain.StudentProfile;
import pe.edu.unp.lanyan.academic.infrastructure.StudentProfileRepository;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentProfileRulesTest {
    private UserAccount account() {
        return new UserAccount("Estudiante FICTICIO", "student@example.test", "NOT_A_REAL_HASH", Set.of());
    }

    @Test
    void profileNormalizesCodeAndReusesTheIdentity() {
        var account = account();
        var profile = new StudentProfile(account, " test-stu-001 ", null);
        assertThat(profile.getStudentCode()).isEqualTo("TEST-STU-001");
        assertThat(profile.getUser()).isSameAs(account);
        assertThat(profile.getAdmissionDate()).isNull();
        assertThat(profile.getId()).isNotNull();
        assertThat(profile.getCreatedAt()).isEqualTo(profile.getUpdatedAt());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void blankCodesAreRejected(String code) {
        assertThatThrownBy(() -> new StudentProfile(account(), code, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlongCodesAreRejected() {
        assertThatThrownBy(() -> new StudentProfile(account(), "A".repeat(33), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serviceQueriesOnlyTheSuppliedOwnerAndReturnsADto() {
        var repository = mock(StudentProfileRepository.class);
        var account = account();
        var profile = new StudentProfile(account, "TEST-OWN", LocalDate.of(2026, 1, 15));
        when(repository.findByUser_Id(account.getId())).thenReturn(Optional.of(profile));
        var view = new StudentProfileService(repository, org.mockito.Mockito.mock(pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository.class), org.mockito.Mockito.mock(pe.edu.unp.lanyan.audit.application.AuditService.class)).getMyProfile(account.getId());
        assertThat(view.userId()).isEqualTo(account.getId());
        assertThat(view.fullName()).isEqualTo(account.getFullName());
        assertThat(view.email()).isEqualTo(account.getEmail());
        assertThat(view.admissionDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        verify(repository).findByUser_Id(account.getId());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void serviceDoesNotInventAMissingProfile() {
        var repository = mock(StudentProfileRepository.class);
        var id = UUID.randomUUID();
        when(repository.findByUser_Id(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new StudentProfileService(repository, org.mockito.Mockito.mock(pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository.class), org.mockito.Mockito.mock(pe.edu.unp.lanyan.audit.application.AuditService.class)).getMyProfile(id))
                .isInstanceOf(StudentProfileNotFoundException.class);
        verify(repository).findByUser_Id(id);
        verifyNoMoreInteractions(repository);
    }
}
