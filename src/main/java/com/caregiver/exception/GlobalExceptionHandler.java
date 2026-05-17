package com.caregiver.exception;

import com.caregiver.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("fk_medication_patient") || msg.contains("patient_id")) {
            return ApiErrorResponse.of("Patient not found. Please complete patient setup or log in again.");
        }
        if (msg.contains("drug_id")) {
            return ApiErrorResponse.of("Medication not found in the drug catalog. Please select a medication from search.");
        }
        return ApiErrorResponse.of("Could not save medication plan. Please check patient and medication details.");
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleRuntimeException(RuntimeException ex) {
        return ApiErrorResponse.of(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        var fe = ex.getBindingResult().getFieldError();
        return ApiErrorResponse.of(fe != null ? fe.getDefaultMessage() : "Validation failed");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolationException(ConstraintViolationException ex) {
        return ApiErrorResponse.of(ex.getMessage());
    }
}
