package com.example.backend.api;

import com.example.backend.repository.SnapshotVersionConflictException;
import com.example.backend.service.AccountAuthenticationException;
import com.example.backend.service.AccountConflictException;
import com.example.backend.service.AccountRequestException;
import com.example.backend.service.WorkspaceAccessDeniedException;
import com.example.backend.service.WorkspaceFinancialSnapshotConflictException;
import com.example.backend.service.WorkspaceFinancialSnapshotNotFoundException;
import com.example.backend.service.WorkspaceMigrationConflictException;
import com.example.backend.service.WorkspaceMigrationNotFoundException;
import com.example.backend.service.WorkspaceMigrationRequestException;
import com.example.backend.service.WorkspaceSelectionException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(SnapshotVersionConflictException.class)
  public ProblemDetail handleSnapshotVersionConflict(SnapshotVersionConflictException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "The financial snapshot changed after it was loaded. Reload before saving.");
    problemDetail.setTitle("Financial snapshot conflict");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(WorkspaceSelectionException.class)
  public ProblemDetail handleWorkspaceSelection(WorkspaceSelectionException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problemDetail.setTitle("Invalid workspace selection");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(WorkspaceAccessDeniedException.class)
  public ProblemDetail handleWorkspaceAccessDenied(WorkspaceAccessDeniedException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    problemDetail.setTitle("Workspace access denied");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(WorkspaceFinancialSnapshotNotFoundException.class)
  public ProblemDetail handleWorkspaceFinancialSnapshotNotFound(
      WorkspaceFinancialSnapshotNotFoundException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problemDetail.setTitle("Financial snapshot not found");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(WorkspaceFinancialSnapshotConflictException.class)
  public ProblemDetail handleWorkspaceFinancialSnapshotConflict(
      WorkspaceFinancialSnapshotConflictException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problemDetail.setTitle("Financial snapshot already exists");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(WorkspaceMigrationRequestException.class)
  public ProblemDetail handleWorkspaceMigrationRequest(
      WorkspaceMigrationRequestException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problemDetail.setTitle("Invalid migration request");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(WorkspaceMigrationConflictException.class)
  public ProblemDetail handleWorkspaceMigrationConflict(
      WorkspaceMigrationConflictException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problemDetail.setTitle("Migration conflict");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(WorkspaceMigrationNotFoundException.class)
  public ProblemDetail handleWorkspaceMigrationNotFound(
      WorkspaceMigrationNotFoundException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problemDetail.setTitle("Migration resource not found");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(AccountAuthenticationException.class)
  public ProblemDetail handleAccountAuthentication(AccountAuthenticationException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    problemDetail.setTitle("Authentication failed");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(AccountConflictException.class)
  public ProblemDetail handleAccountConflict(AccountConflictException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problemDetail.setTitle("Account already exists");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(AccountRequestException.class)
  public ProblemDetail handleAccountRequest(AccountRequestException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problemDetail.setTitle("Invalid request");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    List<String> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map((error) -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    problemDetail.setTitle("Invalid request");
    problemDetail.setProperty("errors", errors);
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    List<String> errors =
        exception.getConstraintViolations().stream()
            .map((violation) -> violation.getPropertyPath() + ": " + violation.getMessage())
            .toList();
    problemDetail.setTitle("Invalid request");
    problemDetail.setProperty("errors", errors);
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request body is malformed or cannot be parsed");
    problemDetail.setTitle("Malformed request");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request path or parameter has an invalid value");
    problemDetail.setTitle("Invalid request");
    problemDetail.setProperty(
        "errors", List.of(exception.getName() + ": expected " + expectedType(exception)));
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type is not supported for this endpoint");
    problemDetail.setTitle("Unsupported media type");
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleResponseStatus(ResponseStatusException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    problemDetail.setTitle(exception.getStatusCode().toString());
    return withRequestId(problemDetail);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleIllegalState(IllegalStateException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "The financial snapshot could not be processed");
    problemDetail.setTitle("Persistence failure");
    return withRequestId(problemDetail);
  }

  private ProblemDetail withRequestId(ProblemDetail problemDetail) {
    String requestId = MDC.get("requestId");
    if (requestId != null) {
      problemDetail.setProperty("requestId", requestId);
    }
    return problemDetail;
  }

  private String expectedType(MethodArgumentTypeMismatchException exception) {
    Class<?> requiredType = exception.getRequiredType();
    if (requiredType == null) {
      return "a valid value";
    }

    return switch (requiredType.getSimpleName()) {
      case "long", "Long" -> "a whole number";
      case "int", "Integer" -> "an integer";
      default -> requiredType.getSimpleName();
    };
  }
}
