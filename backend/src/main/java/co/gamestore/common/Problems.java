package co.gamestore.common;

import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds the RFC 9457 documents from the error catalog, with the type URI the brand is configured
 * with. The catalog stays a plain enum, and the one thing that depends on the domain lives here.
 */
@Component
public class Problems {

    private final String baseUri;

    public Problems(BrandProperties brand) {
        this.baseUri = brand.problemBaseUri().endsWith("/") ? brand.problemBaseUri() : brand.problemBaseUri() + "/";
    }

    public ProblemDetail of(ProblemType type, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                type.status(), detail == null ? type.title() : detail);
        problem.setType(URI.create(baseUri + type.code()));
        problem.setTitle(type.title());
        problem.setProperty("code", type.code());
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        return problem;
    }
}
