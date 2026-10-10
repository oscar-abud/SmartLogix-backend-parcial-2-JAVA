package cl.duoc.users.service.impl;

import cl.duoc.users.dto.UsuarioCreateRequest;
import cl.duoc.users.dto.UsuarioResponse;
import cl.duoc.users.dto.UsuarioUpdateRequest;
import cl.duoc.users.exception.ConflictException;
import cl.duoc.users.exception.ResourceNotFoundException;
import cl.duoc.users.model.Usuario;
import cl.duoc.users.repository.UsuarioRepository;
import cl.duoc.users.service.RoleValidator;
import cl.duoc.users.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RoleValidator roleValidator;

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarActivos() {
        return usuarioRepository.findByActiveTrue().stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(Long id) {
        return UsuarioResponse.from(buscar(id));
    }

    @Override
    @Transactional
    public UsuarioResponse crear(UsuarioCreateRequest request, String rolSolicitante) {
        roleValidator.requireAdmin(rolSolicitante);

        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ConflictException("Ya existe un usuario con el email " + request.email());
        }
        if (usuarioRepository.existsByRut(request.rut())) {
            throw new ConflictException("Ya existe un usuario con el rut " + request.rut());
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.nombre())
                .email(request.email())
                .rut(request.rut())
                .telefono(request.telefono())
                .password(request.password())
                .rol(request.rol())
                .active(true)
                .build();
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional
    public UsuarioResponse actualizarParcial(Long id, UsuarioUpdateRequest request) {
        Usuario usuario = buscar(id);
        if (request.nombre() != null) {
            usuario.setNombre(request.nombre());
        }
        if (request.telefono() != null) {
            usuario.setTelefono(request.telefono());
        }
        if (request.password() != null) {
            usuario.setPassword(request.password());
        }
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional
    public UsuarioResponse cambiarEstado(Long id, Boolean active, String rolSolicitante) {
        roleValidator.requireAdmin(rolSolicitante);
        Usuario usuario = buscar(id);
        usuario.setActive(active);
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional
    public void eliminar(Long id, String rolSolicitante) {
        roleValidator.requireAdmin(rolSolicitante);
        Usuario usuario = buscar(id);
        usuarioRepository.delete(usuario);
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario con id " + id + " no encontrado"));
    }
}
