package in.adivritti.core.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure mode into the same error envelope.
 *
 * <p>Previously only {@code NotFoundException} and {@code IllegalArgumentException}
 * were mapped. A malformed UUID in the path, an unparseable body, or a constraint
 * violation each fell through to Spring's default error page — a 500 with an HTML
 * body and, in development, a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(String errorCode, String message, Map<String, Object> details,
        ZonedDateTime at, String path) {}

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorBody> notFound(NotFoundException e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, e.getErrorCode(), e.getMessage(), Map.of(), req);
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ErrorBody> forbidden(ForbiddenException e, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, e.getErrorCode(), e.getMessage(), Map.of(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorBody> badRequest(IllegalArgumentException e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), Map.of(), req);
    }

    /** Bean-validation failures on an @Valid request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalidBody(MethodArgumentNotValidException e,
        HttpServletRequest req) {
        Map<String, Object> details = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
            details.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors().forEach(ge ->
            details.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
            "Request validation failed", details, req);
    }

    /** Bean-validation failures on @PathVariable / @RequestParam. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorBody> invalidParams(HandlerMethodValidationException e,
        HttpServletRequest req) {
        Map<String, Object> details = new TreeMap<>();
        e.getAllValidationResults().forEach(result -> result.getResolvableErrors().forEach(err ->
            details.putIfAbsent(result.getMethodParameter().getParameterName(),
                err.getDefaultMessage())));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
            "Request validation failed", details, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorBody> constraintViolation(ConstraintViolationException e,
        HttpServletRequest req) {
        Map<String, Object> details = new TreeMap<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            details.put(String.valueOf(v.getPropertyPath()), v.getMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
            "Request validation failed", details, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e,
        HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
            "Request body is missing or malformed", Map.of(), req);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class})
    ResponseEntity<ErrorBody> badParam(Exception e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
            "A request parameter has the wrong type or is missing", Map.of(), req);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ErrorBody> noHandler(Exception e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint", Map.of(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> dataIntegrity(DataIntegrityViolationException e,
        HttpServletRequest req) {
        // Log the detail, return a generic message: constraint names leak schema.
        log.error("Data integrity violation on {} {}", req.getMethod(), req.getRequestURI(), e);
        return build(HttpStatus.CONFLICT, "CONFLICT",
            "The request conflicts with existing data", Map.of(), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "An unexpected error occurred", Map.of(), req);
    }

    private static ResponseEntity<ErrorBody> build(HttpStatus status, String code, String message,
        Map<String, Object> details, HttpServletRequest req) {
        Map<String, Object> safeDetails = new LinkedHashMap<>(details);
        safeDetails.put("status", status.value());
        return ResponseEntity.status(status).body(new ErrorBody(code, message, safeDetails,
            ZonedDateTime.now(ZoneOffset.UTC), req == null ? null : req.getRequestURI()));
    }
}
