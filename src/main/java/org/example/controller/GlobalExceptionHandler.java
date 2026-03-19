package org.example.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * GlobalExceptionHandler
 *
 * Catches exceptions thrown from any @RestController and converts them
 * into clean, structured JSON error responses.
 *
 * Why do we need this?
 * Without it, Spring returns a verbose HTML error page (or a raw 500 JSON
 * with a full stack trace) when something goes wrong. That's bad for:
 *   - API callers who expect consistent JSON
 *   - Security (stack traces leak internal class names)
 *   - Debugging (the error message is buried in noise)
 *
 * What we handle:
 *
 *   MissingServletRequestParameterException
 *     Thrown when a required @RequestParam is absent from the request.
 *     e.g. GET /home?city=bangalore  (missing "time")
 *     → 400 Bad Request with a clear message naming the missing param.
 *
 *   IllegalArgumentException
 *     Thrown by LocalTime.parse() inside TimeUtil/TimeBucketUtil when
 *     the time format is invalid.
 *     e.g. GET /home?city=bangalore&time=25:99  (invalid time)
 *     → 400 Bad Request explaining the expected format.
 *
 *   Generic Exception (catch-all)
 *     Any unexpected error that wasn't caught elsewhere.
 *     → 500 Internal Server Error with a safe generic message.
 *     Stack trace is logged but NOT sent to the client.
 *
 * Response shape (consistent across all errors):
 * {
 *   "timestamp": "2026-03-19T14:30:00",
 *   "status":    400,
 *   "error":     "Bad Request",
 *   "message":   "Missing required parameter: time"
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Missing required parameter: " + ex.getParameterName()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        // LocalTime.parse() throws this on invalid time format
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid parameter value — time must be in HH:MM format (e.g. 09:30, 21:00)"
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        // Log full details server-side, but never expose internals to callers
        System.err.println("[GlobalExceptionHandler] Unhandled exception: " + ex.getMessage());
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again."
        );
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().withNano(0).toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        return ResponseEntity.status(status).body(body);
    }
}