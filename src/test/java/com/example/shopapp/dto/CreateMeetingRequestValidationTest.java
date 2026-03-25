package com.example.shopapp.dto;

import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateMeetingRequestValidationTest {

    private final Validator validator;

    CreateMeetingRequestValidationTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Test
    void boundaryValue_titleLength_255Valid_256Invalid() {
        CreateMeetingRequest valid = baseRequest();
        valid.setTitle("a".repeat(255));

        Set<ConstraintViolation<CreateMeetingRequest>> validViolations = validator.validate(valid);
        assertTrue(validViolations.isEmpty());

        CreateMeetingRequest invalid = baseRequest();
        invalid.setTitle("a".repeat(256));

        Set<ConstraintViolation<CreateMeetingRequest>> invalidViolations = validator.validate(invalid);
        assertFalse(invalidViolations.isEmpty());
    }

    private CreateMeetingRequest baseRequest() {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle("Sprint meeting");
        request.setAgenda("Review progress");
        request.setStartTime(LocalDateTime.now().plusDays(1));
        request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        request.setSyncWithGoogleCalendar(false);
        request.setTimezone("Asia/Ho_Chi_Minh");
        return request;
    }
}
