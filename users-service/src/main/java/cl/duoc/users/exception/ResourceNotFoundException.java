package cl.duoc.users.exception;

/** Se traduce a HTTP 404 Not Found en {@link GlobalExceptionHandler}. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
