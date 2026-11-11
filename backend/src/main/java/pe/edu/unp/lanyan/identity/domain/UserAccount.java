package pe.edu.unp.lanyan.identity.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "identity_users")
public class UserAccount {
    @Id
    private UUID id;
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    @ManyToMany
    @JoinTable(name = "identity_user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_name"))
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() { }

    public UserAccount(String fullName, String email, String passwordHash, Set<Role> roles) {
        this.id = UUID.randomUUID();
        this.fullName = fullName.trim();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.roles.addAll(roles);
    }

    public static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }

    public void replaceRoles(Set<Role> roles) {
        this.roles.clear();
        this.roles.addAll(roles);
        this.updatedAt = Instant.now();
    }

    public void update(String fullName, String email, boolean active, Set<Role> roles) {
        this.fullName = fullName.trim();
        this.email = normalizeEmail(email);
        this.active = active;
        replaceRoles(roles);
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }
}
