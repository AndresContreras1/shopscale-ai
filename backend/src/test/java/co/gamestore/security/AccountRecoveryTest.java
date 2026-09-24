package co.gamestore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gamestore.ContainersConfig;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The checklist for account recovery: no way to learn whether an address is registered, a token that
 * works once and briefly, and a reset that ends every session the old password could reach.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ContainersConfig.class, AccountRecoveryTest.CapturingMessenger.class})
@AutoConfigureMockMvc
class AccountRecoveryTest {

    private static final String PASSWORD = "una tarde tranquila de sabado";
    private static final String NEW_PASSWORD = "otra manana muy tranquila";

    @Autowired
    MockMvc mvc;

    @Autowired
    CapturingMessenger messenger;

    @BeforeEach
    void clearCapturedLinks() {
        messenger.verifications.clear();
        messenger.resets.clear();
    }

    @Test
    void verifiesAnAddressOnceAndOnlyOnce() throws Exception {
        String email = "verify-me@gamestore.co";
        register(email);
        String token = messenger.verifications.getLast();

        mvc.perform(post("/api/auth/verify-email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("token", token)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/verify-email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("token", token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("token-invalid"));
    }

    @Test
    void registersAnAccountThatStartsUnverified() throws Exception {
        Cookie[] session = register("fresh@gamestore.co");

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    /** Both answers are identical, so the endpoint cannot be used to test whether an address shops here. */
    @Test
    void answersTheSameForAnAddressThatExistsAndOneThatDoesNot() throws Exception {
        register("known@gamestore.co");

        forgot("known@gamestore.co");
        forgot("nobody-at-all@gamestore.co");

        assertThat(messenger.resets).hasSize(1);
    }

    @Test
    void resetsThePasswordAndEndsEverySessionTheOldOneCouldReach() throws Exception {
        String email = "reset-me@gamestore.co";
        register(email);
        Cookie[] openSession = login(email, PASSWORD);
        mvc.perform(get("/api/auth/me").cookie(openSession)).andExpect(status().isOk());

        forgot(email);
        reset(messenger.resets.getLast(), NEW_PASSWORD).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(openSession)).andExpect(status().isUnauthorized());
        assertThat(login(email, NEW_PASSWORD)).isNotEmpty();
    }

    @Test
    void refusesAResetTokenThatHasAlreadyBeenUsed() throws Exception {
        String email = "reuse@gamestore.co";
        register(email);
        forgot(email);
        String token = messenger.resets.getLast();

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        reset(token, "a completely different phrase").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("token-invalid"));
    }

    @Test
    void refusesATokenItNeverIssued() throws Exception {
        reset("this-token-was-never-issued-anywhere", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("token-invalid"));
    }

    @Test
    void appliesThePasswordPolicyToTheNewPassword() throws Exception {
        String email = "weak-reset@gamestore.co";
        register(email);
        forgot(email);

        reset(messenger.resets.getLast(), "short123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("password-rejected"));
    }

    /** Asking again retires the previous link, so an old message left in an inbox stops working. */
    @Test
    void keepsOnlyTheNewestResetLinkAlive() throws Exception {
        String email = "newest@gamestore.co";
        register(email);
        forgot(email);
        String first = messenger.resets.getLast();
        forgot(email);
        String second = messenger.resets.getLast();

        reset(first, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
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

    private Cookie[] login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .header("X-Forwarded-For", nextAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    private void forgot(String email) throws Exception {
        mvc.perform(post("/api/auth/password/forgot").with(csrf())
                        .header("X-Forwarded-For", nextAddress())
                        .contentType(MediaType.APPLICATION_JSON).content(json("email", email)))
                .andExpect(status().isAccepted());
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/password/reset").with(csrf())
                .header("X-Forwarded-For", nextAddress())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password)));
    }

    private static String json(String field, String value) {
        return "{\"%s\":\"%s\"}".formatted(field, value);
    }

    /** A fresh address per call, so the per-address limit never interferes with what is being tested. */
    private static String nextAddress() {
        int n = ADDRESS.incrementAndGet();
        return "10.20." + (n / 250) + "." + (n % 250 + 1);
    }

    private static final java.util.concurrent.atomic.AtomicInteger ADDRESS =
            new java.util.concurrent.atomic.AtomicInteger();

    /** Stands in for the email provider and keeps the tokens the tests need to follow the link. */
    @TestConfiguration(proxyBeanMethods = false)
    static class CapturingMessenger implements AccountMessenger {

        final List<String> verifications = new ArrayList<>();
        final List<String> resets = new ArrayList<>();
        final AtomicReference<String> last = new AtomicReference<>();

        @Bean
        @Primary
        AccountMessenger capturingAccountMessenger() {
            return this;
        }

        @Override
        public void sendEmailVerification(User user, String token) {
            verifications.add(token);
            last.set(token);
        }

        @Override
        public void sendPasswordReset(User user, String token) {
            resets.add(token);
            last.set(token);
        }
    }
}
