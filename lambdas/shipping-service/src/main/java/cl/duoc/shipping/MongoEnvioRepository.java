package cl.duoc.shipping;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

/**
 * Implementacion sobre la coleccion "envios" de MongoDB Atlas.
 *
 * El MongoClient se mantiene estatico a proposito: Lambda reutiliza el contenedor
 * entre invocaciones calientes, de modo que la conexion se abre una sola vez y las
 * siguientes ejecuciones no pagan el coste del handshake TLS.
 *
 * Nota de infraestructura: la funcion corre fuera de VPC, por lo que su IP de salida
 * no es fija. Atlas debe tener Network Access abierto a 0.0.0.0/0.
 */
public class MongoEnvioRepository implements EnvioRepository {

    private static final String DB_POR_DEFECTO = "smartlogix";
    private static final String COLECCION = "envios";

    private static volatile MongoClient clienteCompartido;

    private final MongoCollection<Document> envios;

    public MongoEnvioRepository(String uri, String nombreBaseDatos) {
        MongoClient cliente = obtenerCliente(uri);
        String db = (nombreBaseDatos == null || nombreBaseDatos.isBlank())
                ? DB_POR_DEFECTO : nombreBaseDatos;
        this.envios = cliente.getDatabase(db).getCollection(COLECCION);
        // Indice unico: es lo que hace idempotente la reentrega de un mismo mensaje.
        this.envios.createIndex(Indexes.ascending("pedidoId"), new IndexOptions().unique(true));
    }

    private static MongoClient obtenerCliente(String uri) {
        if (clienteCompartido == null) {
            synchronized (MongoEnvioRepository.class) {
                if (clienteCompartido == null) {
                    clienteCompartido = MongoClients.create(uri);
                }
            }
        }
        return clienteCompartido;
    }

    @Override
    public boolean guardarSiNoExiste(Document envio) {
        try {
            envios.insertOne(envio);
            return true;
        } catch (MongoWriteException ex) {
            if (ex.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw ex;
        }
    }
}
