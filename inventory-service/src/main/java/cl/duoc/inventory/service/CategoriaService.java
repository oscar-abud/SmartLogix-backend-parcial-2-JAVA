package cl.duoc.inventory.service;

import cl.duoc.inventory.dto.CategoriaRequest;
import cl.duoc.inventory.dto.CategoriaResponse;

import java.util.List;

public interface CategoriaService {

    List<CategoriaResponse> listar();

    CategoriaResponse crear(CategoriaRequest request);
}
