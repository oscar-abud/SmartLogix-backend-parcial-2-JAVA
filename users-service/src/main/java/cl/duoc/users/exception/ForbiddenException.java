package cl.duoc.users.exception;

/** Se traduce a HTTP 403 Forbidden en {@link GlobalExceptionHandler}. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
