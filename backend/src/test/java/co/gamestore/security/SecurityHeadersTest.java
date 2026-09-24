package co.gamestore.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gamestore.ContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Headers are easy to add and easy to lose in a refactor, so they are asserted rather than trusted.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContainersConfig.class)
@AutoConfigureMockMvc
class SecurityHeadersTest {

    @Autowired
    MockMvc mvc;

    @Test
    void sendsThePolicyHeadersOnEveryApiResponse() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string("Permissions-Policy",
                        "geolocation=(), camera=(), microphone=(), payment=(), usb=(), interest-cohort=()"));
    }

    /** Answers hold prices, stock and personal data. None of it belongs in a shared cache. */
    @Test
    void tellsCachesToKeepNothing() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"));
    }

    /** The browser only remembers the rule when it hears it over a connection that was already secure. */
    @Test
    void demandsHttpsForAYearOnceTheConnectionIsSecure() throws Exception {
        mvc.perform(get("/api/products").secure(true))
                .andExpect(header().string("Strict-Transport-Security",
                        "max-age=31536000 ; includeSubDomains ; preload"));
    }

    @Test
    void doesNotSendHstsOverPlainHttpWhereItWouldBeIgnored() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    /** The documentation needs inline styles to render, and it gets them without the API sharing them. */
    @Test
    void keepsTheLooserPolicyForTheDocumentationOnly() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("'unsafe-inline'")));

        mvc.perform(get("/api/products"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("'unsafe-inline'"))));
    }
}
