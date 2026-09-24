package co.gamestore.ai;

import co.gamestore.ai.AiDtos.AiReport;
import co.gamestore.ai.AiDtos.AiStatus;
import co.gamestore.ai.AiDtos.ReportRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiReportService reportService;

    @GetMapping("/status")
    public AiStatus status() {
        return reportService.status();
    }

    @PostMapping("/reports/inventory")
    public AiReport inventory(@Valid @RequestBody(required = false) ReportRequest request) {
        return reportService.inventoryReport(language(request));
    }

    @PostMapping("/reports/sales")
    public AiReport sales(@Valid @RequestBody(required = false) ReportRequest request) {
        return reportService.salesReport(language(request));
    }

    @PostMapping("/products/{productId}/description")
    public AiReport description(@PathVariable Long productId, @Valid @RequestBody(required = false) ReportRequest request) {
        return reportService.productDescription(productId, language(request));
    }

    private static String language(ReportRequest request) {
        return request == null ? "en" : request.languageOrDefault();
    }
}
