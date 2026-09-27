package in.adivritti.core.common.exception;

import java.time.ZonedDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(String errorCode, String message, Map<String, Object> details, ZonedDateTime at) {}

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorBody> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorBody(e.getErrorCode(), e.getMessage(), Map.of(), ZonedDateTime.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorBody> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorBody("BAD_REQUEST", e.getMessage(), Map.of(), ZonedDateTime.now()));
    }
}
