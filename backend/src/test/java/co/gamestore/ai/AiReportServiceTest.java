package co.gamestore.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import co.gamestore.ai.AiDtos.AiReport;
import co.gamestore.analytics.AnalyticsService;
import co.gamestore.audit.AuditService;
import co.gamestore.catalog.ProductRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AiReportServiceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AiReportService reportService;

    @Autowired
    RuleBasedReportWriter writer;

    @Autowired
    AnalyticsService analytics;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    AuditService auditService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void inventoryReportIsBuiltFromComputedFacts() {
        AiReport report = reportService.inventoryReport("en");

        assertThat(report.provider()).isEqualTo("mock");
        assertThat(report.markdown()).contains("## Restock now");
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = (Map<String, Object>) report.facts();
        assertThat(facts).containsKeys("inventoryKpis", "restockNow", "slowMovers");
    }

    @Test
    void reportsCanBeWrittenInSpanish() {
        assertThat(reportService.salesReport("es").markdown()).contains("Resumen de ventas");
    }

    @Test
    void fallsBackToRuleBasedWriterWhenTheProviderFails() {
        AiClient failing = new AiClient() {
            public String provider() { return "gemini"; }
            public String model() { return "test"; }
            public boolean isConfigured() { return true; }
            public String complete(String s, String u) { throw new IllegalStateException("429 quota exceeded"); }
        };
        var service = new AiReportService(failing, writer, analytics, productRepository, auditService, objectMapper);

        AiReport report = service.inventoryReport("en");

        assertThat(report.fallback()).isTrue();
        assertThat(report.markdown()).contains("## Executive summary");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardExposesKpisAndRestockAlerts() throws Exception {
        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.orders30d").isNumber())
                .andExpect(jsonPath("$.dailySales.length()").value(30))
                .andExpect(jsonPath("$.restockNow").isArray());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void aiReportsAreAdminOnly() throws Exception {
        mvc.perform(post("/api/ai/reports/inventory").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
