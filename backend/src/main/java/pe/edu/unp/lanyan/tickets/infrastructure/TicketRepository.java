package pe.edu.unp.lanyan.tickets.infrastructure;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pe.edu.unp.lanyan.tickets.domain.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    @EntityGraph(attributePaths = "service")
    Page<Ticket> findByRequester_Id(UUID requesterId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "service")
    Page<Ticket> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"service", "requester"})
    Optional<Ticket> findById(UUID id);

    /** Dos técnicos no toman ni cambian el mismo ticket a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findForUpdate(UUID id);

    @Query(value = "SELECT nextval('ticket_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
