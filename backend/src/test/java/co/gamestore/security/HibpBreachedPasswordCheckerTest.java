package co.gamestore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The k-anonymity exchange, stubbed. "password" hashes with SHA-1 to
 * 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8, so only 5BAA6 may ever leave this process.
 */
class HibpBreachedPasswordCheckerTest {

    private static final String PREFIX = "5BAA6";
    private static final String SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HibpBreachedPasswordChecker checker =
            new HibpBreachedPasswordChecker(builder.baseUrl("https://hibp.test").build());

    @Test
    void sendsOnlyTheFirstFiveCharactersOfTheHash() {
        server.expect(requestTo("https://hibp.test/range/" + PREFIX))
                .andRespond(withSuccess(SUFFIX + ":12917", MediaType.TEXT_PLAIN));

        boolean breached = checker.isBreached("password");

        assertThat(breached).isTrue();
        server.verify();
    }

    @Test
    void reportsCleanWhenTheSuffixIsNotInTheList() {
        server.expect(requestTo("https://hibp.test/range/" + PREFIX))
                .andRespond(withSuccess("0000000000000000000000000000000000A:3", MediaType.TEXT_PLAIN));

        assertThat(checker.isBreached("password")).isFalse();
    }

    /** Padded responses carry decoy entries with a count of zero so the reply size says nothing. */
    @Test
    void ignoresThePaddingEntries() {
        server.expect(requestTo("https://hibp.test/range/" + PREFIX))
                .andRespond(withSuccess(SUFFIX + ":0", MediaType.TEXT_PLAIN));

        assertThat(checker.isBreached("password")).isFalse();
    }

    /** A breach check that turns into an outage is worse than one that occasionally misses. */
    @Test
    void letsThePasswordThroughWhenTheServiceIsDown() {
        server.expect(requestTo("https://hibp.test/range/" + PREFIX)).andRespond(withServerError());

        assertThat(checker.isBreached("password")).isFalse();
    }
}
