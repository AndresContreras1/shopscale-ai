package co.gamestore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gamestore.ContainersConfig;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@Import(ContainersConfig.class)
@AutoConfigureMockMvc
class SecurityApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SessionRepository<?> sessionRepository;

    @Test
    void catalogIsPublic() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    void inventoryRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("unauthenticated"))
                .andExpect(jsonPath("$.type").value("https://gamestore.co/problems/unauthenticated"));
    }

    @Test
    void customersCannotSeeInventory() throws Exception {
        Cookie[] session = login("customer@gamestore.co", "Demo-Customer-2026!", "10.0.0.1");
        mvc.perform(get("/api/inventory").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void operatorsCanSeeInventoryButCannotEditPrices() throws Exception {
        Cookie[] session = login("operator@gamestore.co", "Demo-Operator-2026!", "10.0.0.2");
        mvc.perform(get("/api/inventory").cookie(session)).andExpect(status().isOk());
        mvc.perform(post("/api/products").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTheWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).header("X-Forwarded-For", "10.0.0.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@gamestore.co\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The acceptance criterion for replicas: the session is not in this process's memory, it is a row
     * in the shared store, so the next request can be served by any instance.
     */
    @Test
    void storesTheSessionWhereEveryReplicaCanReadIt() throws Exception {
        Cookie[] cookies = login("admin@gamestore.co", "Demo-Admin-2026!", "10.0.0.5");
        String sessionCookie = Arrays.stream(cookies)
                .filter(cookie -> !"XSRF-TOKEN".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow();

        Session stored = sessionRepository.findById(decode(sessionCookie));

        assertThat(stored).isNotNull();
        assertThat(stored.<Object>getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
    }

    @Test
    void signsOutByInvalidatingTheSession() throws Exception {
        Cookie[] session = login("customer@gamestore.co", "Demo-Customer-2026!", "10.0.0.6");
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesToRegisterAPasswordThatIsTooShort() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).header("X-Forwarded-For", "10.3.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"weak@gamestore.co\",\"password\":\"short1234\","
                                + "\"fullName\":\"Weak Password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("password-rejected"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("at least 12")));
    }

    /**
     * A per-address limit does nothing against an attacker with a list of addresses, so the account
     * itself keeps score. After the budget is spent even the correct password is refused, and the
     * answer never reveals that the account is being throttled.
     */
    @Test
    void stopsAnsweringAnAccountAfterTooManyWrongPasswords() throws Exception {
        String email = "throttled@gamestore.co";
        String password = "una tarde tranquila de sabado";
        mvc.perform(post("/api/auth/register").with(csrf()).header("X-Forwarded-For", "10.4.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"Throttled\"}"
                                .formatted(email, password)))
                .andExpect(status().isCreated());

        for (int attempt = 0; attempt < 8; attempt++) {
            mvc.perform(post("/api/auth/login").with(csrf()).header("X-Forwarded-For", "10.4.0." + (attempt + 2))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"wrong guess here\"}".formatted(email)))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login").with(csrf()).header("X-Forwarded-For", "10.4.9.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The per-address limit, tested with a different account each time so the per-account limit is not
     * what stops it. This is the attacker who sprays one password across many accounts.
     */
    @Test
    void blocksLoginBruteForceFromOneAddress() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/api/auth/login").with(csrf()).header("X-Forwarded-For", "10.9.9.9")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"spray-%d@gamestore.co\",\"password\":\"guess me please\"}"
                            .formatted(attempt)));
        }

        mvc.perform(post("/api/auth/login").with(csrf()).header("X-Forwarded-For", "10.9.9.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"spray-last@gamestore.co\",\"password\":\"guess me please\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private Cookie[] login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse().getCookies();
    }

    /** Spring Session sends the id base64 encoded in the cookie. */
    private static String decode(String cookieValue) {
        return new String(Base64.getDecoder().decode(cookieValue));
    }
}
