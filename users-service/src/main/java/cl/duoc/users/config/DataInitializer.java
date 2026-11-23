package cl.duoc.users.config;

import cl.duoc.users.model.Usuario;
import cl.duoc.users.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra los dos usuarios iniciales la primera vez que arranca el servicio.
 *
 * Es necesario porque el login ahora consulta la tabla "usuarios": sin este seed
 * no existiria ningun usuario con el que obtener el primer token, y crear usuarios
 * requiere ser ADMIN.
 *
 * Solo actua si la tabla esta vacia, por lo que es seguro en cada redeploy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${auth.admin.usuario}")
    private String adminEmail;

    @Value("${auth.admin.password}")
    private String adminPassword;

    @Value("${auth.operador.usuario}")
    private String operadorEmail;

    @Value("${auth.operador.password}")
    private String operadorPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Seed de usuarios desactivado (auth.seed.enabled=false)");
            return;
        }
        if (usuarioRepository.count() > 0) {
            log.info("La tabla usuarios ya tiene datos, se omite el seed inicial");
            return;
        }

        usuarioRepository.save(Usuario.builder()
                .nombre("Administrador SmartLogix")
                .email(adminEmail)
                .rut("11111111-1")
                .telefono("+56900000001")
                .password(passwordEncoder.encode(adminPassword))
                .rol("ADMIN")
                .active(true)
                .build());

        usuarioRepository.save(Usuario.builder()
                .nombre("Operador SmartLogix")
                .email(operadorEmail)
                .rut("22222222-2")
                .telefono("+56900000002")
                .password(passwordEncoder.encode(operadorPassword))
                .rol("OPERADOR")
                .active(true)
                .build());

        log.info("Seed inicial creado: {} (ADMIN) y {} (OPERADOR)", adminEmail, operadorEmail);
    }
}
