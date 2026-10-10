package cl.duoc.inventory.exception;

/** Se traduce a HTTP 409 Conflict en {@link GlobalExceptionHandler}. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
