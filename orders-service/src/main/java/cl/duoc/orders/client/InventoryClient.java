package cl.duoc.orders.client;

import cl.duoc.orders.dto.ProductoInventarioDto;
import cl.duoc.orders.exception.ResourceNotFoundException;
import cl.duoc.orders.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia inventory-service. Se usa para obtener el precio real de cada producto,
 * de modo que el cliente nunca pueda imponer el precio de un pedido.
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
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
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
}
