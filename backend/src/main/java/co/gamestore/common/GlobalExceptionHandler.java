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

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException ex, HttpServletRequest req) {
        return ProblemType.NOT_FOUND.toProblem(ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail business(BusinessException ex, HttpServletRequest req) {
        return ProblemType.BUSINESS_RULE.toProblem(ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return ProblemType.CONCURRENT_UPDATE.toProblem(
                "The resource was modified by another request, please retry", req.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthenticated(AuthenticationException ex, HttpServletRequest req) {
        return ProblemType.UNAUTHENTICATED.toProblem(ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(AccessDeniedException ex, HttpServletRequest req) {
        return ProblemType.FORBIDDEN.toProblem(null, req.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return ProblemType.BAD_REQUEST.toProblem(ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail problem = ProblemType.VALIDATION_FAILED.toProblem(null, req.getRequestURI());
        problem.setProperty("fieldErrors", fields);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {}", req.getRequestURI(), ex);
        return ProblemType.INTERNAL.toProblem(null, req.getRequestURI());
    }
}
