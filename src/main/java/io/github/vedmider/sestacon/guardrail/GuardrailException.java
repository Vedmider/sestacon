package io.github.vedmider.sestacon.guardrail;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class GuardrailException extends RuntimeException {

    public GuardrailException(String message) {
        super(message);
    }
}
