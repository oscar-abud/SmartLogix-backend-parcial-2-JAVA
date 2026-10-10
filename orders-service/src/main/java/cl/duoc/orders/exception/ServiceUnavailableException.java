package cl.duoc.orders.exception;

/** Se traduce a HTTP 503 Service Unavailable en {@link GlobalExceptionHandler}. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
