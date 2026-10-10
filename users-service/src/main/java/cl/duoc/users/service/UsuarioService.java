package cl.duoc.users.service;

import cl.duoc.users.dto.UsuarioCreateRequest;
import cl.duoc.users.dto.UsuarioResponse;
import cl.duoc.users.dto.UsuarioUpdateRequest;

import java.util.List;

public interface UsuarioService {

    List<UsuarioResponse> listarActivos();

    UsuarioResponse obtenerPorId(Long id);

    UsuarioResponse crear(UsuarioCreateRequest request, String rolSolicitante);

    UsuarioResponse actualizarParcial(Long id, UsuarioUpdateRequest request);

    UsuarioResponse cambiarEstado(Long id, Boolean active, String rolSolicitante);

    void eliminar(Long id, String rolSolicitante);
}
