package cl.duoc.users.service;

import cl.duoc.users.dto.UsuarioCreateRequest;
import cl.duoc.users.dto.UsuarioResponse;
import cl.duoc.users.dto.UsuarioUpdateRequest;

import java.util.List;

/**
 * El control de acceso ya no se pasa como parametro: lo resuelve Spring Security
 * con PreAuthorize a partir del rol que viene firmado en el JWT.
 */
public interface UsuarioService {

    List<UsuarioResponse> listarActivos();

    UsuarioResponse obtenerPorId(Long id);

    UsuarioResponse crear(UsuarioCreateRequest request);

    UsuarioResponse actualizarParcial(Long id, UsuarioUpdateRequest request);

    UsuarioResponse cambiarEstado(Long id, Boolean active);

    void eliminar(Long id);
}
