package co.gamestore.scalability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gamestore.ContainersConfig;
import co.gamestore.catalog.ProductRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(ContainersConfig.class)
@AutoConfigureMockMvc
class ScalabilityTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ProductRepository productRepository;

    @Test
    void everyResponseSaysWhichInstanceServedIt() throws Exception {
        mvc.perform(get("/api/products")).andExpect(header().exists("X-Served-By"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void importsCsvInTheBackground() throws Exception {
        String csv = """
                sku,name,brand,category_slug,price,stock
                IMP-TEST-001,Imported Lamp,Lumo,home-kitchen,19.90,40
                ELEC-PHN-002,Smartphone Nova Lite 64GB,Nova,electronics,239.00,0
                bad sku,Broken,Acme,electronics,10,1
                IMP-TEST-002,Unknown category,Acme,toys,10,1
                """;
        var file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        String body = mvc.perform(multipart("/api/products/import").with(csrf()).file(file))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(body).get("id").asText();

        JsonNode job = null;
        for (int i = 0; i < 50; i++) {
            job = objectMapper.readTree(mvc.perform(get("/api/products/import/" + jobId))
                    .andReturn().getResponse().getContentAsString());
            if ("COMPLETED".equals(job.get("status").asText())) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(job.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(job.get("created").asInt()).isEqualTo(1);
        assertThat(job.get("updated").asInt()).isEqualTo(1);
        assertThat(job.get("failed").asInt()).isEqualTo(2);
        assertThat(productRepository.findBySku("ELEC-PHN-002").orElseThrow().getPrice()).isEqualByComparingTo("239.00");
    }

    @Test
    void importIsAdminOnly() throws Exception {
        mvc.perform(get("/api/products/import/any")).andExpect(status().isUnauthorized());
    }
}
