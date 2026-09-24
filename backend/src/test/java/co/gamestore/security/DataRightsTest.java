package co.gamestore.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gamestore.ContainersConfig;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The habeas data rights, exercised the way a customer would: see what was agreed, change it, take a
 * copy, and leave.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContainersConfig.class)
@AutoConfigureMockMvc
class DataRightsTest {

    private static final String PASSWORD = "una tarde tranquila de sabado";
    private static final AtomicInteger ADDRESS = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    /** Registering agrees to the handling of the account and to nothing else. */
    @Test
    void recordsOnlyTheConsentThatRegisteringActuallyGives() throws Exception {
        Cookie[] session = register("consent-start@gamestore.co");

        mvc.perform(get("/api/me/consents").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.DATA_PROCESSING").value(true))
                .andExpect(jsonPath("$.MARKETING_EMAIL").value(false))
                .andExpect(jsonPath("$.MARKETING_WHATSAPP").value(false))
                .andExpect(jsonPath("$.ANALYTICS").value(false));
    }

    @Test
    void letsSomebodyAgreeToMarketingAndChangeTheirMindAgain() throws Exception {
        Cookie[] session = register("consent-change@gamestore.co");

        updateConsents(session, true).andExpect(jsonPath("$.MARKETING_EMAIL").value(true));
        updateConsents(session, false).andExpect(jsonPath("$.MARKETING_EMAIL").value(false));

        // Revoking adds an entry rather than erasing one, so the history is still there to prove.
        mvc.perform(get("/api/me/export").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents.length()").value(3));
    }

    @Test
    void handsOverACopyOfEverythingAsAFile() throws Exception {
        Cookie[] session = register("export-me@gamestore.co");

        mvc.perform(get("/api/me/export").cookie(session))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition", "attachment; filename=\"my-data.json\""))
                .andExpect(jsonPath("$.profile.email").value("export-me@gamestore.co"))
                .andExpect(jsonPath("$.consents").isArray())
                .andExpect(jsonPath("$.orders.count").value(0));
    }

    /**
     * Deletion ends the account and the sessions. The orders survive because an invoice has to, but
     * they no longer carry the address of a person.
     */
    @Test
    void deletesTheAccountAndSignsEveryDeviceOut() throws Exception {
        String email = "delete-me@gamestore.co";
        Cookie[] session = register(email);

        mvc.perform(delete("/api/me").with(csrf()).cookie(session)).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).header("X-Forwarded-For", nextAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAllOfThisToSomebodyWhoIsNotSignedIn() throws Exception {
        mvc.perform(get("/api/me/consents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/export")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/me").with(csrf())).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions updateConsents(Cookie[] session, boolean granted)
            throws Exception {
        return mvc.perform(put("/api/me/consents").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consents\":{\"MARKETING_EMAIL\":%s}}".formatted(granted)))
                .andExpect(status().isOk());
    }

    private Cookie[] register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf())
                        .header("X-Forwarded-For", nextAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"Test Person\"}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookies();
    }

    private static String nextAddress() {
        int n = ADDRESS.incrementAndGet();
        return "10.40." + (n / 250) + "." + (n % 250 + 1);
    }
}
