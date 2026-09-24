package co.gamestore.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns every exception into an RFC 9457 problem document, served as application/problem+json.
 * Internal details never reach the client: the stack trace goes to the log, the client gets a code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Problems problems;

    public GlobalExceptionHandler(Problems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException ex, HttpServletRequest req) {
        return problems.of(ProblemType.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(ProblemException.class)
    public ProblemDetail problem(ProblemException ex, HttpServletRequest req) {
        return problems.of(ex.type(), ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail business(BusinessException ex, HttpServletRequest req) {
        return problems.of(ProblemType.BUSINESS_RULE, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return problems.of(ProblemType.CONCURRENT_UPDATE,
                "The resource was modified by another request, please retry", req.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthenticated(AuthenticationException ex, HttpServletRequest req) {
        return problems.of(ProblemType.UNAUTHENTICATED, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(AccessDeniedException ex, HttpServletRequest req) {
        return problems.of(ProblemType.FORBIDDEN, null, req.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return problems.of(ProblemType.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail problem = problems.of(ProblemType.VALIDATION_FAILED, null, req.getRequestURI());
        problem.setProperty("fieldErrors", fields);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {}", req.getRequestURI(), ex);
        return problems.of(ProblemType.INTERNAL, null, req.getRequestURI());
    }
}
