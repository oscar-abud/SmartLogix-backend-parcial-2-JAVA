package cl.duoc.orders.exception;

/** Se traduce a HTTP 400 Bad Request en {@link GlobalExceptionHandler}. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
