package pe.edu.unp.lanyan.tickets.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.unp.lanyan.tickets.domain.TicketComment;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {
    List<TicketComment> findByTicket_IdOrderByCreatedAt(UUID ticketId);
    List<TicketComment> findByTicket_IdAndInternalFalseOrderByCreatedAt(UUID ticketId);
}
