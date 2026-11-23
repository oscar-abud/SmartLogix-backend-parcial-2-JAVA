package cl.duoc.orders.client;

import cl.duoc.orders.dto.ProductoInventarioDto;
import cl.duoc.orders.exception.ResourceNotFoundException;
import cl.duoc.orders.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Cliente HTTP hacia inventory-service. Se usa para obtener el precio real de cada producto,
 * de modo que el cliente nunca pueda imponer el precio de un pedido.
 *
 * Desde la EP3 inventory-service exige JWT, por lo que este cliente propaga la cabecera
 * Authorization de la peticion en curso. Es el mismo usuario quien consulta el inventario,
 * asi que no hacen falta credenciales de servicio ni una ruta interna sin autenticar.
 */
@Slf4j
@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(RestClient.Builder builder,
                           @Value("${inventory.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInitializer(request -> tokenDeLaPeticionActual()
                        .ifPresent(token -> request.getHeaders().set(HttpHeaders.AUTHORIZATION, token)))
                .build();
    }

    public ProductoInventarioDto obtenerProducto(Long productoId) {
        try {
            return restClient.get()
                    .uri("/api/products/{id}", productoId)
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (req, res) -> {
                        throw new ResourceNotFoundException(
                                "Producto con id " + productoId + " no existe en inventory-service");
                    })
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new ServiceUnavailableException(
                                "inventory-service respondio con error " + res.getStatusCode().value());
                    })
                    .body(ProductoInventarioDto.class);
        } catch (ResourceAccessException ex) {
            log.error("No fue posible conectar con inventory-service", ex);
            throw new ServiceUnavailableException(
                    "inventory-service no esta disponible. Intente nuevamente mas tarde");
        }
    }

    /**
     * Devuelve la cabecera Authorization de la peticion HTTP que se esta atendiendo.
     * Vacio si se invoca fuera de un contexto web (por ejemplo desde un hilo propio),
     * en cuyo caso la llamada saldra sin token y inventory-service respondera 401.
     */
    private java.util.Optional<String> tokenDeLaPeticionActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return java.util.Optional.ofNullable(attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION));
        }
        return java.util.Optional.empty();
    }
}
