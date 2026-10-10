package cl.duoc.users.exception;

/** Se traduce a HTTP 401 Unauthorized en {@link GlobalExceptionHandler}. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
