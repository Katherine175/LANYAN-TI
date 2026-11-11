package pe.edu.unp.lanyan.academic.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.unp.lanyan.academic.api.CatalogDtos.CatalogCourseView;
import pe.edu.unp.lanyan.academic.application.CourseCatalogService;

@RestController
@RequestMapping("/api/v1/courses/catalog")
public class CourseCatalogController {
    private final CourseCatalogService catalog;
    public CourseCatalogController(CourseCatalogService catalog) { this.catalog = catalog; }

    @GetMapping
    public List<CatalogCourseView> catalog() { return catalog.catalog(); }
}
