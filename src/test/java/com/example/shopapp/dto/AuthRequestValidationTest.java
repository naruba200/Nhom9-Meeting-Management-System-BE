package com.example.shopapp.dto;

import com.example.shopapp.dto.auth.LoginRequest;
import com.example.shopapp.dto.auth.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRequestValidationTest {

    private final Validator validator;

    AuthRequestValidationTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Test
    void loginRequest_requiresValidEmailAndPasswordLength() {
        LoginRequest invalid = new LoginRequest();
        invalid.setEmail("invalid-email");
        invalid.setPassword("123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(invalid);
        assertFalse(violations.isEmpty());

        LoginRequest valid = new LoginRequest();
        valid.setEmail("user@example.com");
        valid.setPassword("password123");

        Set<ConstraintViolation<LoginRequest>> validViolations = validator.validate(valid);
        assertTrue(validViolations.isEmpty());
    }

    @Test
    void registerRequest_requiresPhonePatternAndRequiredFields() {
        RegisterRequest invalid = new RegisterRequest();
        invalid.setFullName("");
        invalid.setEmail("test");
        invalid.setPassword("1234");
        invalid.setPhone("09123abc");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(invalid);
        assertFalse(violations.isEmpty());

        RegisterRequest valid = new RegisterRequest();
        valid.setFullName("Nguyen Van A");
        valid.setEmail("nguyenvana@example.com");
        valid.setPassword("password123");
        valid.setPhone("0912345678");

        Set<ConstraintViolation<RegisterRequest>> validViolations = validator.validate(valid);
        assertTrue(validViolations.isEmpty());
    }
}
