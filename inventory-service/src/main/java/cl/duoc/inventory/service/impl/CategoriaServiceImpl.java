package cl.duoc.inventory.service.impl;

import cl.duoc.inventory.dto.CategoriaRequest;
import cl.duoc.inventory.dto.CategoriaResponse;
import cl.duoc.inventory.exception.ConflictException;
import cl.duoc.inventory.model.Categoria;
import cl.duoc.inventory.repository.CategoriaRepository;
import cl.duoc.inventory.service.CategoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAll().stream()
                .map(CategoriaResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CategoriaResponse crear(CategoriaRequest request) {
        if (categoriaRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ConflictException("Ya existe una categoria con el nombre " + request.nombre());
        }
        Categoria categoria = Categoria.builder()
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .build();
        return CategoriaResponse.from(categoriaRepository.save(categoria));
    }
}
