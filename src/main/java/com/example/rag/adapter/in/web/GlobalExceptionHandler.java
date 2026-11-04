package com.example.rag.adapter.in.web;

import com.example.rag.guardrail.GuardrailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Consistent, non-leaky error contract. Spring MVC's standard 4xx (unreadable body,
 * unsupported media type, missing params, {@code ResponseStatusException} from the
 * controllers) are handled by the superclass; guardrail rejections map to 400; anything
 * genuinely unexpected becomes a generic 500 with no internal details in the body.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GuardrailException.class)
    ProblemDetail handleGuardrail(GuardrailException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("event=unhandled_error type={} outcome=error500", e.getClass().getName(), e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error");
    }
}
