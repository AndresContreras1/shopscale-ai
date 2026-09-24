package co.gamestore.common;

/**
 * Thrown when the right answer is a specific entry from the error catalog rather than a generic
 * business failure. Saves inventing an exception class per error while keeping the response precise.
 */
public class ProblemException extends RuntimeException {

    private final transient ProblemType type;

    public ProblemException(ProblemType type, String detail) {
        super(detail);
        this.type = type;
    }

    public ProblemType type() {
        return type;
    }
}
