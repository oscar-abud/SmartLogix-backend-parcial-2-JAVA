-- Categorias iniciales (idempotente: no se duplican al reiniciar)
INSERT INTO categorias (id, nombre, descripcion) VALUES
    (1, 'Electronica', 'Computadores, perifericos y dispositivos electronicos'),
    (2, 'Hogar', 'Articulos para el hogar y la cocina'),
    (3, 'Oficina', 'Insumos y mobiliario de oficina')
ON CONFLICT (id) DO NOTHING;

-- Ajusta la secuencia para que los nuevos registros no choquen con los ids sembrados
SELECT setval(pg_get_serial_sequence('categorias', 'id'), GREATEST((SELECT MAX(id) FROM categorias), 1));
