package io.github.setl.exception;

/**
 * UnknownException
 */
public class UnknownException extends BaseException {

    public UnknownException(String errorMessage) {
        super(errorMessage);
    }

    public static class Storage extends UnknownException {
        public Storage(String errorMessage) {
            super(errorMessage);
        }
    }

    public static class Format extends UnknownException {
        public Format(String errorMessage) {
            super(errorMessage);
        }
    }

    public static class Environment extends UnknownException {
        public Environment(String errorMessage) {
            super(errorMessage);
        }
    }

    public static class ValueType extends UnknownException {
        public ValueType(String errorMessage) {
            super(errorMessage);
        }
    }
}

