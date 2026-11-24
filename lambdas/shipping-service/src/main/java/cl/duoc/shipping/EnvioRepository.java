package cl.duoc.shipping;

import org.bson.Document;

/**
 * Puerto de persistencia de envios. Tenerlo como interfaz permite probar el
 * handler sin levantar ni depender de un MongoDB Atlas real en el pipeline.
 */
public interface EnvioRepository {

    /**
     * @return true si el envio se inserto, false si ya existia uno para ese pedido
     *         (SQS entrega "al menos una vez", asi que las reentregas son normales)
     */
    boolean guardarSiNoExiste(Document envio);
}
