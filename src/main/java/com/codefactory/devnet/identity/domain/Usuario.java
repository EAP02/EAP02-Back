package com.codefactory.devnet.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Usuario del dominio: estado de la cuenta y reglas de acceso.
 *
 * <p>Sin JPA ni Spring, por la regla 2 de {@code FronterasModularesTest}. Eso permite
 * probar cada regla de HU-06 con un test unitario puro, sin levantar contexto ni base
 * de datos, que es exactamente lo que hace {@code UsuarioTest}.</p>
 *
 * <p>No lleva el correo ni el hash de la clave mas alla de lo necesario para decidir:
 * la verificacion de la contrasena ocurre fuera, porque el algoritmo es una decision
 * de infraestructura y el dominio no debe conocerlo.</p>
 */
public class Usuario {

    private final UUID id;
    private final String nombreUsuario;
    private final String claveHash;
    private final EstadoUsuario estado;
    private final boolean mfaHabilitado;
    private final Set<Rol> roles;

    private short intentosFallidos;
    private Instant bloqueadoHasta;
    private Instant ultimoAccesoEn;

    public Usuario(UUID id,
                   String nombreUsuario,
                   String claveHash,
                   EstadoUsuario estado,
                   boolean mfaHabilitado,
                   short intentosFallidos,
                   Instant bloqueadoHasta,
                   Set<Rol> roles) {
        this.id = id;
        this.nombreUsuario = nombreUsuario;
        this.claveHash = claveHash;
        this.estado = estado;
        this.mfaHabilitado = mfaHabilitado;
        this.intentosFallidos = intentosFallidos;
        this.bloqueadoHasta = bloqueadoHasta;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    // ------------------------------------------------------------------
    // Reglas de HU-06
    // ------------------------------------------------------------------

    /**
     * Permisos efectivos, aplanados desde todos los roles.
     *
     * <p><b>Devuelve vacio si el usuario tiene MFA pendiente.</b> Un moderador que no
     * ha inscrito su segundo factor se autentica, pero no ejerce ninguna capacidad de
     * su rol. Resolverlo aqui, y no en cada endpoint, es lo que garantiza que no haya
     * una sola ruta por la que se escape (lineamiento 3.4 y 6.2).</p>
     */
    public Set<String> permisosEfectivos() {
        if (tieneMfaPendiente()) {
            return Set.of();
        }
        return roles.stream()
                .flatMap(rol -> rol.permisos().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean tienePermiso(String codigo) {
        return permisosEfectivos().contains(codigo);
    }

    /** Algun rol exige segundo factor y el usuario todavia no lo inscribio. */
    public boolean tieneMfaPendiente() {
        return roles.stream().anyMatch(Rol::exigeMfa) && !mfaHabilitado;
    }

    public Set<String> codigosDeRol() {
        return roles.stream().map(Rol::codigo).collect(Collectors.toUnmodifiableSet());
    }

    /** Bloqueo temporal por intentos fallidos. */
    public boolean estaBloqueada() {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(Instant.now());
    }

    public boolean puedeAutenticarse() {
        return estado.puedeAutenticarse() && !estaBloqueada();
    }

    /**
     * Puede crear o modificar contenido.
     *
     * <p>Una cuenta suspendida conserva la lectura: la sancion limita la
     * participacion, no convierte al usuario en invisible para si mismo.</p>
     */
    public boolean puedeEscribir() {
        return estado.puedeEscribir() && !estaBloqueada();
    }

    // ------------------------------------------------------------------
    // Transiciones
    // ------------------------------------------------------------------

    public void registrarAccesoExitoso() {
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
        this.ultimoAccesoEn = Instant.now();
    }

    /**
     * Suma un intento fallido y bloquea al alcanzar el maximo.
     *
     * <p>Al bloquear se reinicia el contador, para que el siguiente ciclo empiece
     * limpio tras cumplirse el bloqueo en vez de volver a bloquear en cada intento.</p>
     *
     * <p>Lineamiento 3.4: bloqueo ante intentos fallidos, y <b>sin</b> exigir cambios
     * periodicos de contrasena sin indicio de compromiso.</p>
     *
     * @return {@code true} si este intento fue el que provoco el bloqueo
     */
    public boolean registrarIntentoFallido(int maximo, Duration duracionBloqueo) {
        this.intentosFallidos++;
        if (this.intentosFallidos >= maximo) {
            this.bloqueadoHasta = Instant.now().plus(duracionBloqueo);
            this.intentosFallidos = 0;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------

    public UUID id() {
        return id;
    }

    public String nombreUsuario() {
        return nombreUsuario;
    }

    public String claveHash() {
        return claveHash;
    }

    public EstadoUsuario estado() {
        return estado;
    }

    public boolean mfaHabilitado() {
        return mfaHabilitado;
    }

    public short intentosFallidos() {
        return intentosFallidos;
    }

    public Instant bloqueadoHasta() {
        return bloqueadoHasta;
    }

    public Instant ultimoAccesoEn() {
        return ultimoAccesoEn;
    }

    /** Sin correo ni hash: esta clase puede acabar en un log o en una traza. */
    @Override
    public String toString() {
        return "Usuario{id=%s, nombreUsuario=%s, estado=%s}".formatted(id, nombreUsuario, estado);
    }
}