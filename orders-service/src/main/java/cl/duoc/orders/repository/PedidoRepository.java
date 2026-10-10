package cl.duoc.orders.repository;

import cl.duoc.orders.model.Pedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /** Trae el pedido junto con sus detalles en una sola consulta. */
    @EntityGraph(attributePaths = "detalles")
    Optional<Pedido> findWithDetallesById(Long id);
}
