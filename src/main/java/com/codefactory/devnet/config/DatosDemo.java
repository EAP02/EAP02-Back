package com.codefactory.devnet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Usuarios de demostracion para el perfil {@code local}.
 *
 * <p><b>No es una migracion de Flyway a proposito.</b> Un hash de Argon2id no se puede
 * escribir a mano en un {@code .sql}: depende de los parametros configurados en
 * {@code SeguridadConfig}. Generarlo aqui con el mismo {@link PasswordEncoder} que usa
 * la aplicacion garantiza que siempre coincida.</p>
 *
 * <p>Y sobre todo: asi estas credenciales <b>nunca</b> pueden llegar a produccion. Una
 * migracion se aplica en todos los entornos; este componente solo existe bajo el perfil
 * local.</p>
 *
 * <p>Es idempotente: si los usuarios ya estan, no hace nada.</p>
 */
@Component
@Profile("local")
public class DatosDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);

    /** Igual para todos, solo en local. */
    public static final String CLAVE_DEMO = "Devnet2026!";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder codificador;

    public DatosDemo(JdbcTemplate jdbc, PasswordEncoder codificador) {
        this.jdbc = jdbc;
        this.codificador = codificador;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Integer existentes = jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE correo LIKE '%@devnet.test'", Integer.class);

        if (existentes != null && existentes > 0) {
            log.info("Datos de demostracion ya presentes ({} usuarios). No se recrean.", existentes);
            return;
        }

        String hash = codificador.encode(CLAVE_DEMO);

        crear("dev@devnet.test", "dev_ana", "Ana Restrepo",
                "Backend developer", "DESARROLLADOR", false, hash);

        crear("mod@devnet.test", "mod_carlos", "Carlos Duque",
                "Moderador de comunidad", "MODERADOR", true, hash);

        // Mismo rol que el anterior pero SIN segundo factor inscrito. Sirve para ver
        // que un MODERADOR con MFA pendiente se autentica pero sale sin permisos
        // (lineamiento 3.4).
        crear("mod-sin-mfa@devnet.test", "mod_lucia", "Lucia Marin",
                "Moderadora sin MFA", "MODERADOR", false, hash);

        crear("admin@devnet.test", "admin_sofia", "Sofia Gomez",
                "Administradora", "ADMIN", true, hash);

        log.info("""

                ==========================================================
                  DATOS DE DEMOSTRACION CREADOS  (solo perfil local)
                  Clave para todos: {}

                    dev@devnet.test           DESARROLLADOR
                    mod@devnet.test           MODERADOR  (MFA inscrito)
                    mod-sin-mfa@devnet.test   MODERADOR  (MFA pendiente)
                    admin@devnet.test         ADMIN

                  Swagger UI: http://localhost:8080/swagger-ui.html
                ==========================================================
                """, CLAVE_DEMO);
    }

    private void crear(String correo, String nombreUsuario, String nombreCompleto,
                       String titular, String rol, boolean mfaInscrito, String hash) {

        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO usuario (id, correo, nombre_usuario, clave_hash, estado, mfa_habilitado)
                VALUES (?, ?, ?, ?, 'ACTIVO', ?)
                """, id, correo, nombreUsuario, hash, mfaInscrito);

        jdbc.update("""
                INSERT INTO usuario_rol (usuario_id, rol_id)
                SELECT ?, id FROM rol WHERE codigo = ?
                """, id, rol);

        // El perfil es obligatorio para publicar (HU-07, criterio 1).
        jdbc.update("""
                INSERT INTO perfil (usuario_id, nombre_completo, titular, disponible_colaborar)
                VALUES (?, ?, ?, true)
                """, id, nombreCompleto, titular);

        // Al menos una tecnologia declarada, para que el perfil este completo.
        jdbc.update("""
                INSERT INTO perfil_tecnologia (usuario_id, tecnologia_id, nivel, anios)
                SELECT ?, id, 'AVANZADO', 3 FROM tecnologia WHERE slug = 'java'
                """, id);

        // Identidad de GitHub vinculada: es lo que marca el perfil como verificado
        // y sustituye a la verificacion por correo (ADR-004).
        jdbc.update("""
                INSERT INTO identidad_externa (usuario_id, proveedor, sujeto_externo, usuario_externo)
                VALUES (?, 'GITHUB', ?, ?)
                """, id, "demo-" + nombreUsuario, nombreUsuario);
    }
}