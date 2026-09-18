package com.codefactory.devnet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Crea usuarios con rol, perfil e identidad verificada para las pruebas de integracion.
 *
 * <p>No reutiliza {@code DatosDemo} a proposito: ese componente solo existe bajo el
 * perfil {@code local} y crea siempre los mismos cuatro usuarios. Aqui cada prueba
 * necesita los suyos, con el rol exacto que va a ejercitar.</p>
 */
@Component
public class SoporteDatosPrueba {

    public static final String CLAVE = "Prueba2026!";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder codificador;

    public SoporteDatosPrueba(JdbcTemplate jdbc, PasswordEncoder codificador) {
        this.jdbc = jdbc;
        this.codificador = codificador;
    }

    /**
     * @param rol         codigo de {@code rol}: DESARROLLADOR, MODERADOR, ADMIN...
     * @param mfaInscrito para roles que lo exigen, decide si el token saldra con
     *                    permisos o vacio
     * @return correo con el que autenticarse
     */
    public UsuarioCreado crear(String rol, boolean mfaInscrito) {
        UUID id = UUID.randomUUID();
        String sufijo = id.toString().substring(0, 8);
        String correo = rol.toLowerCase() + "-" + sufijo + "@prueba.test";
        String nombreUsuario = rol.toLowerCase() + "_" + sufijo;

        jdbc.update("""
                INSERT INTO usuario (id, correo, nombre_usuario, clave_hash, estado, mfa_habilitado)
                VALUES (?, ?, ?, ?, 'ACTIVO', ?)
                """, id, correo, nombreUsuario, codificador.encode(CLAVE), mfaInscrito);

        jdbc.update("INSERT INTO usuario_rol (usuario_id, rol_id) SELECT ?, id FROM rol WHERE codigo = ?",
                id, rol);

        jdbc.update("""
                INSERT INTO perfil (usuario_id, nombre_completo, disponible_colaborar)
                VALUES (?, ?, true)
                """, id, "Usuario " + sufijo);

        jdbc.update("""
                INSERT INTO perfil_tecnologia (usuario_id, tecnologia_id, nivel)
                SELECT ?, id, 'AVANZADO' FROM tecnologia WHERE slug = 'java'
                """, id);

        jdbc.update("""
                INSERT INTO identidad_externa (usuario_id, proveedor, sujeto_externo, usuario_externo)
                VALUES (?, 'GITHUB', ?, ?)
                """, id, "test-" + sufijo, nombreUsuario);

        return new UsuarioCreado(id, correo, nombreUsuario);
    }

    /** Crea la cuenta pero SIN perfil, para probar PROYECTO_PERFIL_INEXISTENTE. */
    public UsuarioCreado crearSinPerfil(String rol) {
        UUID id = UUID.randomUUID();
        String sufijo = id.toString().substring(0, 8);
        String correo = "sinperfil-" + sufijo + "@prueba.test";

        jdbc.update("""
                INSERT INTO usuario (id, correo, nombre_usuario, clave_hash, estado, mfa_habilitado)
                VALUES (?, ?, ?, ?, 'ACTIVO', true)
                """, id, correo, "sinperfil_" + sufijo, codificador.encode(CLAVE));

        jdbc.update("INSERT INTO usuario_rol (usuario_id, rol_id) SELECT ?, id FROM rol WHERE codigo = ?",
                id, rol);

        return new UsuarioCreado(id, correo, "sinperfil_" + sufijo);
    }

    /** Identificadores de tecnologias aprobadas del catalogo semilla. */
    public java.util.List<Short> tecnologiasAprobadas(int cuantas) {
        return jdbc.queryForList(
                "SELECT id FROM tecnologia WHERE aprobada = true ORDER BY id LIMIT ?",
                Short.class, cuantas);
    }

    public int contarEventosAuditoria(String operacion, UUID actorId) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE operacion = ? AND actor_id = ?",
                Integer.class, operacion, actorId);
        return total == null ? 0 : total;
    }

    public String estadoDePublicacion(UUID publicacionId) {
        return jdbc.queryForObject("SELECT estado FROM publicacion WHERE id = ?",
                String.class, publicacionId);
    }

    public record UsuarioCreado(UUID id, String correo, String nombreUsuario) { }
}