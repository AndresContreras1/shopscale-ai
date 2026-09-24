package co.gamestore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gamestore.ContainersConfig;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The whole staff sign-in with a second factor, from enrolment to recovery.
 *
 * <p>Uses the operator account so the admin account stays available to the other tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContainersConfig.class)
@AutoConfigureMockMvc
class StaffMfaTest {

    private static final String EMAIL = "operator@gamestore.co";
    private static final String PASSWORD = "Demo-Operator-2026!";
    private static final AtomicInteger ADDRESS = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void walksAStaffAccountFromEnrolmentToASignInThatNeedsACode() throws Exception {
        // 1. The password alone still works, and the answer says a second factor is expected.
        JsonNode first = signIn(PASSWORD);
        assertThat(first.get("mfaRequired").asBoolean()).isFalse();
        assertThat(first.get("mfaEnrolmentRequired").asBoolean()).isTrue();
        Cookie[] session = lastCookies;

        // 2. Enrol: the secret is handed over once, for the authenticator app.
        String secret = json.readTree(mvc.perform(post("/api/auth/mfa/enrol").with(csrf()).cookie(session))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.provisioningUri").value(org.hamcrest.Matchers.startsWith("otpauth://")))
                        .andReturn().getResponse().getContentAsString())
                .get("secret").asText();

        // 3. Activate by proving the app holds it, and receive the recovery codes.
        List<String> recoveryCodes = readCodes(mvc.perform(post("/api/auth/mfa/activate").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(code(TimeBasedOneTimePassword.codeAt(secret, Instant.now()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(recoveryCodes).hasSize(10);

        // 4. From now on the password buys nothing on its own.
        JsonNode second = signIn(PASSWORD);
        assertThat(second.get("mfaRequired").asBoolean()).isTrue();
        assertThat(second.get("user").isNull()).isTrue();
        Cookie[] pending = lastCookies;
        mvc.perform(get("/api/inventory").cookie(pending)).andExpect(status().isUnauthorized());

        // 5. A wrong code does not open the door.
        mvc.perform(post("/api/auth/mfa/verify").with(csrf()).cookie(pending)
                        .contentType(MediaType.APPLICATION_JSON).content(code("000000")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("mfa-required"));

        // 6. The right code does, and the session works.
        Cookie[] signedIn = verify(pending, TimeBasedOneTimePassword.codeAt(secret, Instant.now()));
        mvc.perform(get("/api/inventory").cookie(signedIn)).andExpect(status().isOk());

        // 7. A recovery code works once, for the phone that was lost.
        signIn(PASSWORD);
        Cookie[] pendingAgain = lastCookies;
        String recovery = recoveryCodes.getFirst();
        Cookie[] recovered = verify(pendingAgain, recovery);
        mvc.perform(get("/api/inventory").cookie(recovered)).andExpect(status().isOk());

        signIn(PASSWORD);
        mvc.perform(post("/api/auth/mfa/verify").with(csrf()).cookie(lastCookies)
                        .contentType(MediaType.APPLICATION_JSON).content(code(recovery)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesACodeWithoutASignInBehindIt() throws Exception {
        mvc.perform(post("/api/auth/mfa/verify").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(code("123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("mfa-required"));
    }

    private Cookie[] lastCookies;

    private JsonNode signIn(String password) throws Exception {
        var response = mvc.perform(post("/api/auth/login").with(csrf()).header("X-Forwarded-For", nextAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        lastCookies = response.getCookies();
        return json.readTree(response.getContentAsString());
    }

    private Cookie[] verify(Cookie[] session, String value) throws Exception {
        return mvc.perform(post("/api/auth/mfa/verify").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content(code(value)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andReturn().getResponse().getCookies();
    }

    private List<String> readCodes(String body) {
        return json.readTree(body).get("recoveryCodes").valueStream().map(JsonNode::asText).toList();
    }

    private static String code(String value) {
        return "{\"code\":\"%s\"}".formatted(value);
    }

    private static String nextAddress() {
        int n = ADDRESS.incrementAndGet();
        return "10.30." + (n / 250) + "." + (n % 250 + 1);
    }
}
