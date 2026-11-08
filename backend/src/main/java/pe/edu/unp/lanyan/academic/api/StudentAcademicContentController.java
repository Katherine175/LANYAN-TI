package pe.edu.unp.lanyan.academic.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.academic.api.AcademicContentDtos.*;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.PageView;
import pe.edu.unp.lanyan.academic.application.*;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;

@RestController
@RequestMapping("/api/v1/students/me")
public class StudentAcademicContentController {
    private final ClassScheduleService schedules;
    private final CourseMaterialService materials;
    public StudentAcademicContentController(ClassScheduleService schedules, CourseMaterialService materials) {
        this.schedules=schedules; this.materials=materials;
    }
    @GetMapping("/schedule")
    public PageView<ScheduleView> schedule(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return schedules.mySchedule(principal.getId(),page,size);
    }
    @GetMapping("/materials")
    public PageView<MaterialView> materials(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return materials.myMaterials(principal.getId(),page,size);
    }
}
