package co.gamestore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gamestore.ContainersConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The double-submit exchange as a browser performs it, with no test shortcuts.
 *
 * <p>This lives apart from {@link SecurityApiTest} on purpose: the {@code csrf()} request
 * post-processor swaps the real token repository for a test one, and once any test in a class has
 * used it, nothing in that class can prove what the real repository does.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContainersConfig.class)
@AutoConfigureMockMvc
// The property is a marker with no meaning to the application: it gives this class its own cached
// application context, and therefore its own servlet context, so no other test can have replaced the
// token repository before these run.
@TestPropertySource(properties = "app.test.isolation=csrf")
class CsrfProtectionTest {

    private static final String LOGIN = "{\"email\":\"customer@gamestore.co\",\"password\":\"Demo-Customer-2026!\"}";

    @Autowired
    MockMvc mvc;

    @Test
    void handsEveryVisitorATokenTheirJavaScriptCanRead() throws Exception {
        Cookie token = mvc.perform(get("/api/products")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(token).isNotNull();
        // Readable on purpose: the page has to copy it into a header. That is safe because an
        // attacker on another origin can make the browser send cookies but cannot read them.
        assertThat(token.isHttpOnly()).isFalse();
    }

    @Test
    void rejectsAPostThatOnlyCarriesCookies() throws Exception {
        Cookie token = issuedToken();

        mvc.perform(post("/api/auth/login").cookie(token).header("X-Forwarded-For", "10.1.0.1")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsAPostThatEchoesTheTokenInTheHeader() throws Exception {
        Cookie token = issuedToken();

        mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .header("X-Forwarded-For", "10.1.0.2")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsATokenThatDoesNotMatchTheCookie() throws Exception {
        Cookie token = issuedToken();

        mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", "not-the-token")
                        .header("X-Forwarded-For", "10.1.0.3")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isForbidden());
    }

    private Cookie issuedToken() throws Exception {
        return mvc.perform(get("/api/products")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }
}
