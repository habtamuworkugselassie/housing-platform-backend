package com.housingplatform.shared.exception;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @Value("${spring.profiles.active:dev}")
  private String activeProfile;

  private boolean isProduction() {
    return "prod".equals(activeProfile) || "production".equals(activeProfile);
  }

  /**
   * Ensures JSON error bodies are written with {@code application/json}. If a controller (e.g. file
   * download) already set {@code Content-Type} to {@code video/mp4} or similar, Spring would
   * otherwise keep that type and fail with HttpMessageNotWritableException when serializing {@link
   * ErrorResponse}.
   */
  private static ResponseEntity<ErrorResponse> json(ErrorResponse body, HttpStatus status) {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
      ResourceNotFoundException ex, WebRequest request) {
    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Resource Not Found")
            .message(ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
    return json(error, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
      BusinessException ex, WebRequest request) {
    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Business Rule Violation")
            .message(ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
    return json(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex, WebRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(this::mapFieldError)
            .collect(Collectors.toList());

    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Invalid input parameters")
            .path(request.getDescription(false).replace("uri=", ""))
            .fieldErrors(fieldErrors)
            .build();
    return json(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolationException(
      ConstraintViolationException ex, WebRequest request) {
    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message(ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
    return json(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler({JwtException.class, AuthenticationServiceException.class})
  public ResponseEntity<ErrorResponse> handleJwtException(Exception ex, WebRequest request) {
    // Check if it's an expired JWT
    Throwable cause = ex.getCause();
    boolean isExpired =
        ex instanceof JwtException
            || (cause != null
                && (cause instanceof ExpiredJwtException
                    || cause.getMessage() != null && cause.getMessage().contains("expired")));

    HttpStatus status = isExpired ? HttpStatus.UNAUTHORIZED : HttpStatus.UNAUTHORIZED;
    String errorMessage =
        isExpired ? "JWT token has expired. Please refresh your token." : "JWT token is invalid.";

    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status.value())
            .error("Authentication Failed")
            .message(errorMessage)
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
    return json(error, status);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
      NoResourceFoundException ex, WebRequest request) {
    // Silently handle missing static resources - this is expected for API-only backend
    // Log at debug level only to avoid noise in logs
    log.debug("Static resource not found: {}", request.getDescription(false));

    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Resource Not Found")
            .message("The requested resource was not found")
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
    return json(error, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponse> handleMissingServletRequestPart(
      MissingServletRequestPartException ex, WebRequest request) {
    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(
                "Missing or invalid form part: "
                    + ex.getRequestPartName()
                    + ". For file uploads, send multipart/form-data with part name 'files'.")
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
    return json(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ErrorResponse> handleMultipartException(
      MultipartException ex, WebRequest request) {
    log.warn("Multipart request failed: {}", ex.getMessage());
    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(
                "Invalid multipart request. Ensure Content-Type is multipart/form-data with a valid boundary and part name 'files'.")
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
    return json(error, HttpStatus.BAD_REQUEST);
  }

  /**
   * Client closed the connection while the server was still writing (common for video Range
   * requests, tab close, seek). Not an application error — avoid ERROR logs and JSON error bodies.
   */
  @ExceptionHandler(ClientAbortException.class)
  public void handleClientAbort(ClientAbortException ex, WebRequest request) {
    log.debug(
        "Client aborted response (disconnect/broken pipe): {}",
        request.getDescription(false).replace("uri=", ""));
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
      NoHandlerFoundException ex, WebRequest request) {
    // Handle 404 for API endpoints that don't exist
    // Log at debug level to reduce noise
    log.debug("No handler found for: {} {}", ex.getHttpMethod(), request.getDescription(false));

    String path = request.getDescription(false).replace("uri=", "");
    String message =
        "The requested endpoint was not found. API endpoints are available under /api/v1/";

    // Special message for root path
    if (path.equals("/") || path.isEmpty()) {
      message = "API is running. Available endpoints: /api/v1/, /actuator/health, /swagger-ui.html";
    }

    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Endpoint Not Found")
            .message(message)
            .path(path)
            .build();
    return json(error, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
    String path = request.getDescription(false).replace("uri=", "");
    if (isClientAbortInChain(ex)) {
      log.debug("Client aborted response (wrapped): {}", path);
      return new ResponseEntity<>(null, HttpStatus.NO_CONTENT);
    }
    Throwable root = ex;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    log.error(
        "Unhandled exception: type={} rootCauseType={} path={}",
        ex.getClass().getName(),
        root.getClass().getName(),
        path,
        ex);

    // In production, don't expose internal error details
    String errorMessage =
        isProduction()
            ? "An internal error occurred. Please try again later or contact support."
            : ex.getMessage();

    ErrorResponse error =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message(errorMessage)
            .path(path)
            .build();
    return json(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private static boolean isClientAbortInChain(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof ClientAbortException) {
        return true;
      }
    }
    return false;
  }

  private ErrorResponse.FieldError mapFieldError(FieldError fieldError) {
    return ErrorResponse.FieldError.builder()
        .field(fieldError.getField())
        .message(fieldError.getDefaultMessage())
        .rejectedValue(fieldError.getRejectedValue())
        .build();
  }
}
