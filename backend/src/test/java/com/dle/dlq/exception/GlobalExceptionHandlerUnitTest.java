package com.dle.dlq.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerUnitTest {

    @Test
    void handle_returns500_withGenericMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<String> resp = handler.handle(new Exception("boom"));

        assertThat(resp.getStatusCodeValue()).isEqualTo(500);
        assertThat(resp.getBody()).isEqualTo("Unexpected error");
    }
}
