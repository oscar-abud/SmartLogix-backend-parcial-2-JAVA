package cl.duoc.inventory.repository;

import cl.duoc.inventory.model.Producto;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Carga la categoria en la misma consulta (evita N+1). */
    @EntityGraph(attributePaths = "categoria")
    List<Producto> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "categoria")
    Optional<Producto> findWithCategoriaById(Long id);

    boolean existsBySku(String sku);
}
