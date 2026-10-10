package cl.duoc.users.repository;

import cl.duoc.users.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    List<Usuario> findByActiveTrue();

    boolean existsByEmail(String email);

    boolean existsByRut(String rut);
}
