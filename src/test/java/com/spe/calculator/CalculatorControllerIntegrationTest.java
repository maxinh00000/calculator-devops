package com.spe.calculator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spring MVC integration tests: full application context, real controller + advice + actuator. */
@SpringBootTest
@AutoConfigureMockMvc
class CalculatorControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void addReturnsJson() throws Exception {
        mvc.perform(get("/api/v1/add").param("a", "2").param("b", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("add"))
                .andExpect(jsonPath("$.a").value(2.0))
                .andExpect(jsonPath("$.b").value(3.0))
                .andExpect(jsonPath("$.result").value(5.0));
    }

    @Test
    void subtract() throws Exception {
        mvc.perform(get("/api/v1/subtract?a=10&b=4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(6.0));
    }

    @Test
    void multiply() throws Exception {
        mvc.perform(get("/api/v1/multiply?a=6&b=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(42.0));
    }

    @Test
    void divide() throws Exception {
        mvc.perform(get("/api/v1/divide?a=7&b=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("divide"))
                .andExpect(jsonPath("$.result").value(3.5));
    }

    @Test
    void divideByZeroReturns400() throws Exception {
        mvc.perform(get("/api/v1/divide?a=5&b=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("Division by zero")))
                .andExpect(jsonPath("$.path").value("/api/v1/divide"));
    }

    @Test
    void missingParameterReturns400() throws Exception {
        mvc.perform(get("/api/v1/add?a=2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("'b'")));
    }

    @Test
    void nonNumericParameterReturns400() throws Exception {
        mvc.perform(get("/api/v1/multiply?a=abc&b=2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("must be a number")));
    }

    @Test
    void nanParameterReturns400() throws Exception {
        mvc.perform(get("/api/v1/add?a=NaN&b=2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("finite")));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
