package com.example.backend.api;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
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
    return problemDetail;
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
    return problemDetail;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request body is malformed or cannot be parsed");
    problemDetail.setTitle("Malformed request");
    return problemDetail;
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request path or parameter has an invalid value");
    problemDetail.setTitle("Invalid request");
    problemDetail.setProperty(
        "errors", List.of(exception.getName() + ": expected " + expectedType(exception)));
    return problemDetail;
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type is not supported for this endpoint");
    problemDetail.setTitle("Unsupported media type");
    return problemDetail;
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleResponseStatus(ResponseStatusException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    problemDetail.setTitle(exception.getStatusCode().toString());
    return problemDetail;
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleIllegalState(IllegalStateException exception) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "The financial snapshot could not be processed");
    problemDetail.setTitle("Persistence failure");
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
