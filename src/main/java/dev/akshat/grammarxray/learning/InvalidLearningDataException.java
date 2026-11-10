package dev.akshat.grammarxray.learning;

public class InvalidLearningDataException extends RuntimeException {

    public InvalidLearningDataException(String message) {
        super(message);
    }

    public InvalidLearningDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
