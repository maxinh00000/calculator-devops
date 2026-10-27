package com.spe.calculator;

import com.spe.calculator.exception.DivisionByZeroException;
import com.spe.calculator.exception.InvalidInputException;
import com.spe.calculator.service.CalculatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure JUnit 5 unit tests: no Spring context, fast. */
class CalculatorServiceTest {

    private static final double DELTA = 1e-9;
    private final CalculatorService service = new CalculatorService();

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource({"2, 3, 5", "-4, 1.5, -2.5", "0, 0, 0", "1e10, 1e10, 2e10"})
    void add(double a, double b, double expected) {
        assertEquals(expected, service.add(a, b), DELTA);
    }

    @ParameterizedTest(name = "{0} - {1} = {2}")
    @CsvSource({"10, 4, 6", "4, 10, -6", "-2.5, -2.5, 0"})
    void subtract(double a, double b, double expected) {
        assertEquals(expected, service.subtract(a, b), DELTA);
    }

    @ParameterizedTest(name = "{0} * {1} = {2}")
    @CsvSource({"6, 7, 42", "-3, 4, -12", "123.45, 0, 0", "0.5, 0.5, 0.25"})
    void multiply(double a, double b, double expected) {
        assertEquals(expected, service.multiply(a, b), DELTA);
    }

    @ParameterizedTest(name = "{0} / {1} = {2}")
    @CsvSource({"10, 2, 5", "7, 2, 3.5", "-9, 3, -3", "0, 5, 0"})
    void divide(double a, double b, double expected) {
        assertEquals(expected, service.divide(a, b), DELTA);
    }

    @Test
    @DisplayName("divide by zero throws DivisionByZeroException")
    void divideByZero() {
        assertThrows(DivisionByZeroException.class, () -> service.divide(5, 0));
    }

    @Test
    @DisplayName("divide by negative zero also throws")
    void divideByNegativeZero() {
        assertThrows(DivisionByZeroException.class, () -> service.divide(5, -0.0));
    }

    @Test
    @DisplayName("NaN / Infinity inputs are rejected")
    void nonFiniteInputsRejected() {
        assertThrows(InvalidInputException.class, () -> service.add(Double.NaN, 1));
        assertThrows(InvalidInputException.class, () -> service.multiply(1, Double.POSITIVE_INFINITY));
    }
}
