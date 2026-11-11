package pe.edu.unp.lanyan.tickets.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.requests.domain.Texts;

/** Seguimiento de un ticket. Una nota interna solo la ve el equipo de TI. */
@Entity
@Table(name = "ticket_comments")
public class TicketComment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_id", nullable = false, updatable = false) private Ticket ticket;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", updatable = false) private UserAccount author;
    @Column(name = "author_name", nullable = false, length = 120, updatable = false) private String authorName;
    @Column(nullable = false, updatable = false) private boolean staff;
    @Column(nullable = false, updatable = false) private boolean internal;
    @Column(nullable = false, length = 2000, updatable = false) private String body;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected TicketComment() { }

    public TicketComment(Ticket ticket, UserAccount author, String authorName, boolean staff, boolean internal, String body, Instant now) {
        if (internal && !staff) throw new IllegalArgumentException("Solo el equipo de TI escribe notas internas.");
        this.id = UUID.randomUUID();
        this.ticket = Objects.requireNonNull(ticket);
        this.author = author;
        this.authorName = Objects.requireNonNull(authorName);
        this.staff = staff;
        this.internal = internal;
        this.body = Texts.required(body, 2000, "Escribe el comentario.");
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getAuthorName() { return authorName; }
    public boolean isStaff() { return staff; }
    public boolean isInternal() { return internal; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
