package com.shopscale.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void catalogIsPublic() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    void inventoryRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void customersCannotSeeInventory() throws Exception {
        String token = login("customer@shopscale.dev", "Customer123!", "10.0.0.1");
        mvc.perform(get("/api/inventory").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorsCanSeeInventoryButCannotEditPrices() throws Exception {
        String token = login("operator@shopscale.dev", "Operator123!", "10.0.0.2");
        mvc.perform(get("/api/inventory").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWrongPasswordAndTamperedTokens() throws Exception {
        mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.0.0.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@shopscale.dev\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        String token = login("admin@shopscale.dev", "Admin123!", "10.0.0.3");
        mvc.perform(get("/api/audit").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blocksLoginBruteForce() throws Exception {
        String body = "{\"email\":\"admin@shopscale.dev\",\"password\":\"guess\"}";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.9.9.9")
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }
        mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.9.9.9")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    private String login(String email, String password, String ip) throws Exception {
        String response = mvc.perform(post("/api/auth/login").header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("token").asText();
    }
}
