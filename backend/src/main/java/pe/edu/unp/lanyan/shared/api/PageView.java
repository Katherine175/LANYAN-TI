package pe.edu.unp.lanyan.shared.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageView<T> of(Page<T> page) {
        return new PageView<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
