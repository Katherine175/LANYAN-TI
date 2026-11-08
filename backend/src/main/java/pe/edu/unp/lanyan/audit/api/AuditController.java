package pe.edu.unp.lanyan.audit.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.shared.api.PageView;

@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) { this.audit = audit; }

    @GetMapping
    public PageView<AuditEventView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return audit.list(page, size);
    }
}
