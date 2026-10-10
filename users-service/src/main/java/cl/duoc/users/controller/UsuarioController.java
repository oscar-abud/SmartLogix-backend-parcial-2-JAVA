package cl.duoc.users.controller;

import cl.duoc.users.dto.StatusRequest;
import cl.duoc.users.dto.UsuarioCreateRequest;
import cl.duoc.users.dto.UsuarioResponse;
import cl.duoc.users.dto.UsuarioUpdateRequest;
import cl.duoc.users.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsuarioController {

    /** Cabecera con el rol de quien realiza la peticion (ADMIN u OPERADOR). */
    public static final String ROLE_HEADER = "X-User-Role";

    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listarActivos() {
        return ResponseEntity.ok(usuarioService.listarActivos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(
            @RequestHeader(value = ROLE_HEADER, required = false) String rol,
            @Valid @RequestBody UsuarioCreateRequest request) {
        UsuarioResponse creado = usuarioService.crear(request, rol);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizarParcial(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioUpdateRequest request) {
        return ResponseEntity.ok(usuarioService.actualizarParcial(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UsuarioResponse> cambiarEstado(
            @RequestHeader(value = ROLE_HEADER, required = false) String rol,
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {
        return ResponseEntity.ok(usuarioService.cambiarEstado(id, request.active(), rol));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @RequestHeader(value = ROLE_HEADER, required = false) String rol,
            @PathVariable Long id) {
        usuarioService.eliminar(id, rol);
        return ResponseEntity.noContent().build();
    }
}
