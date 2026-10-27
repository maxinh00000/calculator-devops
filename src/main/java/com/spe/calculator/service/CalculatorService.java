package com.spe.calculator.service;

import com.spe.calculator.exception.DivisionByZeroException;
import com.spe.calculator.exception.InvalidInputException;
import org.springframework.stereotype.Service;

/**
 * Pure business logic. No web concerns here, so it can be unit-tested directly.
 */
@Service
public class CalculatorService {

    public double add(double a, double b) {
        validate(a, b);
        return a + b;
    }

    public double subtract(double a, double b) {
        validate(a, b);
        return a - b;
    }

    public double multiply(double a, double b) {
        validate(a, b);
        return a * b;
    }

    public double divide(double a, double b) {
        validate(a, b);
        if (b == 0.0) {
            throw new DivisionByZeroException("Division by zero is not allowed (b must be non-zero)");
        }
        return a / b;
    }

    /** Rejects NaN and Infinity, which Java happily parses from "NaN" / "Infinity". */
    private void validate(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            throw new InvalidInputException("Parameters 'a' and 'b' must be finite numbers");
        }
    }
}
