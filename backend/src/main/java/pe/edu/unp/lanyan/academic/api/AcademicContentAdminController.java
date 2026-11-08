package pe.edu.unp.lanyan.academic.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.academic.api.AcademicContentDtos.*;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.PageView;
import pe.edu.unp.lanyan.academic.application.*;

@RestController
@RequestMapping("/api/v1/academic")
public class AcademicContentAdminController {
    private final ClassScheduleService schedules;
    private final CourseMaterialService materials;
    public AcademicContentAdminController(ClassScheduleService schedules, CourseMaterialService materials) {
        this.schedules=schedules; this.materials=materials;
    }

    @PostMapping("/sections/{sectionId}/schedules")
    public ResponseEntity<ScheduleView> createSchedule(@PathVariable UUID sectionId, @Valid @RequestBody ScheduleWriteRequest request) {
        var view=schedules.create(sectionId,request);
        return ResponseEntity.created(URI.create("/api/v1/academic/schedules/"+view.id())).body(view);
    }
    @GetMapping("/sections/{sectionId}/schedules")
    public PageView<ScheduleView> schedules(@PathVariable UUID sectionId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return schedules.list(sectionId,page,size);
    }
    @GetMapping("/schedules/{id}")
    public ScheduleView schedule(@PathVariable UUID id) { return schedules.get(id); }
    @PutMapping("/schedules/{id}")
    public ScheduleView updateSchedule(@PathVariable UUID id,@Valid @RequestBody ScheduleWriteRequest request) {
        return schedules.update(id,request);
    }
    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable UUID id,@RequestParam(required=false) Long version) {
        schedules.delete(id,version); return ResponseEntity.noContent().build();
    }

    @PostMapping("/sections/{sectionId}/materials")
    public ResponseEntity<MaterialView> createMaterial(@PathVariable UUID sectionId,@Valid @RequestBody MaterialWriteRequest request) {
        var view=materials.create(sectionId,request);
        return ResponseEntity.created(URI.create("/api/v1/academic/materials/"+view.id())).body(view);
    }
    @GetMapping("/sections/{sectionId}/materials")
    public PageView<MaterialView> materials(@PathVariable UUID sectionId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return materials.list(sectionId,page,size);
    }
    @GetMapping("/materials/{id}")
    public MaterialView material(@PathVariable UUID id) { return materials.get(id); }
    @PutMapping("/materials/{id}")
    public MaterialView updateMaterial(@PathVariable UUID id,@Valid @RequestBody MaterialWriteRequest request) {
        return materials.update(id,request);
    }
    @DeleteMapping("/materials/{id}")
    public ResponseEntity<Void> deleteMaterial(@PathVariable UUID id,@RequestParam(required=false) Long version) {
        materials.delete(id,version); return ResponseEntity.noContent().build();
    }
}
