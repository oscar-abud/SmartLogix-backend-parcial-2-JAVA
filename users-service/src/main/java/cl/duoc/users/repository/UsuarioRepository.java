package cl.duoc.users.repository;

import cl.duoc.users.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    List<Usuario> findByActiveTrue();

    /** Usado por el login: el email es el identificador del usuario (claim "sub" del JWT). */
    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByRut(String rut);
}
