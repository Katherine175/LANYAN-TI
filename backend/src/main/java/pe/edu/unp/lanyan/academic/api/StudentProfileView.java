package pe.edu.unp.lanyan.academic.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StudentProfileView(UUID id, UUID userId, String studentCode, String fullName,
        String email, LocalDate admissionDate, Instant createdAt, Instant updatedAt) { }
