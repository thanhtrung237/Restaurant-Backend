package com.group.restaurant.exceptions;

import com.group.restaurant.dtos.response.base.ResponseError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.nio.file.AccessDeniedException;
import java.util.Date;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(NOT_FOUND)
    public ResponseError handleResourceNotFoundException(ResourceNotFoundException e, WebRequest request) {
        log.warn("Resource not found at {}: {}", request.getDescription(false), e.getMessage());
        return ResponseError.builder()
                .timestamp(new Date())
                .status(NOT_FOUND.value())
                .error(NOT_FOUND.getReasonPhrase())
                .message(e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(CONFLICT)
    public ResponseError handleConflictException(ConflictException e, WebRequest request) {
        log.warn("Conflict at {}: {}", request.getDescription(false), e.getMessage());
        return ResponseError.builder()
                .timestamp(new Date())
                .status(CONFLICT.value())
                .error(CONFLICT.getReasonPhrase())
                .message(e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(InvalidParamException.class)
    @ResponseStatus(BAD_REQUEST)
    public ResponseError handleInvalidParamException(InvalidParamException e, WebRequest request) {
        log.warn("Invalid param at {}: {}", request.getDescription(false), e.getMessage());
        return ResponseError.builder()
                .timestamp(new Date())
                .status(BAD_REQUEST.value())
                .error(BAD_REQUEST.getReasonPhrase())
                .message(e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(BAD_REQUEST)
    public ResponseError handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        List<String> errorMessages = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        String errorMessage = String.join(", ", errorMessages);
        log.warn("Validation error at {}: {}", request.getDescription(false), errorMessage);

        return ResponseError.builder()
                .timestamp(new Date())
                .status(BAD_REQUEST.value())
                .error(BAD_REQUEST.getReasonPhrase())
                .message(errorMessage)
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(BAD_REQUEST)
    public ResponseError handleIllegalArgumentException(IllegalArgumentException e, WebRequest request) {
        log.warn("Illegal argument at {}: {}", request.getDescription(false), e.getMessage());
        return ResponseError.builder()
                .timestamp(new Date())
                .status(BAD_REQUEST.value())
                .error(BAD_REQUEST.getReasonPhrase())
                .message(e.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(FORBIDDEN)
    public ResponseError handleAccessDeniedException(AccessDeniedException e, WebRequest request) {
        log.warn("Access denied at {}: {}", request.getDescription(false), e.getMessage());
        return ResponseError.builder()
                .timestamp(new Date())
                .status(FORBIDDEN.value())
                .error(FORBIDDEN.getReasonPhrase())
                .message("Truy cập bị từ chối. Bạn không có quyền thực hiện thao tác này.")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(METHOD_NOT_ALLOWED)
    public ResponseError handleMethodNotSupportedException(org.springframework.web.HttpRequestMethodNotSupportedException e, WebRequest request) {
        log.warn("Method not supported at {}: {}", request.getDescription(false), e.getMessage());
        return ResponseError.builder()
                .timestamp(new Date())
                .status(METHOD_NOT_ALLOWED.value())
                .error(METHOD_NOT_ALLOWED.getReasonPhrase())
                .message("Phương thức HTTP '" + e.getMethod() + "' không được hỗ trợ cho endpoint này.")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    @ResponseStatus(BAD_REQUEST)
    public ResponseError handleMissingParams(org.springframework.web.bind.MissingServletRequestParameterException e, WebRequest request) {
        log.warn("Missing parameter at {}: {}", request.getDescription(false), e.getMessage());
        return ResponseError.builder()
                .timestamp(new Date())
                .status(BAD_REQUEST.value())
                .error(BAD_REQUEST.getReasonPhrase())
                .message("Thiếu tham số bắt buộc: '" + e.getParameterName() + "'.")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(BAD_REQUEST)
    public ResponseError handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e, WebRequest request) {
        log.warn("Type mismatch at {}: {}", request.getDescription(false), e.getMessage());
        return ResponseError.builder()
                .timestamp(new Date())
                .status(BAD_REQUEST.value())
                .error(BAD_REQUEST.getReasonPhrase())
                .message("Giá trị tham số '" + e.getName() + "' không đúng định dạng.")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(BAD_REQUEST)
    public ResponseError handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException e, WebRequest request) {
        log.warn("Data integrity violation at {}: {}", request.getDescription(false), e.getMessage());
        return ResponseError.builder()
                .timestamp(new Date())
                .status(BAD_REQUEST.value())
                .error(BAD_REQUEST.getReasonPhrase())
                .message("Lỗi dữ liệu liên kết hoặc trùng lặp dữ liệu trong hệ thống.")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    public ResponseError handleException(Exception ex, WebRequest request) {
        log.error("Unhandled exception at {}: {}", request.getDescription(false), ex.getMessage(), ex);
        return ResponseError.builder()
                .timestamp(new Date())
                .status(INTERNAL_SERVER_ERROR.value())
                .error(INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
    }
}
