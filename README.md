# EP2 — Microservicios Nativos en Nube

Asignatura: *Diseño y Construcción de Soluciones Nativas en Nube* (Duoc UC).

Tres microservicios Spring Boot 3.3 / Java 17 independientes. Cada uno tiene su propio repositorio Maven, su propia base de datos PostgreSQL (*Database per Service*) y arquitectura en capas `controller → service → repository → model` (+ `dto`, `exception`).

| Servicio | Puerto API | BD (puerto host) | Responsabilidad | Documentación |
|---|---|---|---|---|
| users-service | 8081 | users_db (5441) | Usuarios y control de acceso por rol | [README](users-service/README.md) |
| inventory-service | 8082 | inventory_db (5442) | Productos, categorías y stock | [README](inventory-service/README.md) |
| orders-service | 8083 | orders_db (5443) | Pedidos; monto calculado en el backend | [README](orders-service/README.md) |

```
             ┌──────────────────┐      ┌──────────────────────┐      ┌──────────────────┐
  cliente ──▶│  users-service   │      │  inventory-service   │◀─────│  orders-service  │◀── cliente
             │      :8081       │      │        :8082         │ HTTP │      :8083       │
             └────────┬─────────┘      └──────────┬───────────┘ GET  └────────┬─────────┘
                      │                           │      /api/products/{id}   │
                 ┌────▼─────┐               ┌─────▼──────┐              ┌─────▼─────┐
                 │ users_db │               │inventory_db│              │ orders_db │
                 └──────────┘               └────────────┘              └───────────┘
```

## Arranque rápido (todo en Docker)

Requisito: Docker Desktop en ejecución. Levanta los servicios en este orden (orders depende de inventory):

```bash
cd users-service     && docker compose up -d --build && cd ..
cd inventory-service && docker compose up -d --build && cd ..
cd orders-service    && docker compose up -d --build && cd ..
```

Para compilar con Maven en vez de Docker, cada proyecto trae Maven Wrapper: `./mvnw clean package` (requiere JDK 17).

Para detener: `docker compose down` en cada carpeta (agrega `-v` para borrar los datos).

## Etapa posterior

El microservicio de **Shipping** se implementará como **AWS Lambda** con **MongoDB Atlas** (fuera del alcance de esta entrega, que cubre el 80% de la arquitectura).
