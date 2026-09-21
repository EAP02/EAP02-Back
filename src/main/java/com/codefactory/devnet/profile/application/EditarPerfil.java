package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.profile.domain.CodigoErrorPerfil;
import com.codefactory.devnet.profile.domain.Perfil;
import com.codefactory.devnet.profile.domain.PerfilNoEncontradoException;
import com.codefactory.devnet.profile.domain.PoliticaPerfil;
import com.codefactory.devnet.profile.domain.RepositorioPerfiles;
import com.codefactory.devnet.profile.domain.TecnologiaDeclarada;
import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EditarPerfil {

    private final RepositorioPerfiles perfiles;
    private final UsuarioDirectorio directorio;

    public EditarPerfil(RepositorioPerfiles perfiles, UsuarioDirectorio directorio) {
        this.perfiles = perfiles;
        this.directorio = directorio;
    }

    @Transactional
    public PerfilDetalle ejecutar(UUID id, String nombre, String biografia,
                                  List<TecnologiaDeclarada> tecnologias,
                                  String githubUrl, String linkedinUrl) {

        // La identidad sale SIEMPRE del token, nunca de la ruta ni del cuerpo.
        // Se comprueba antes de leer el perfil: si no eres el titular, ni siquiera
        // llegas a saber si ese perfil existe por diferencia de tiempos.
        UUID autenticado = directorio.autenticado()
                .map(UsuarioDirectorio.UsuarioResumen::id)
                .orElse(null);
        PoliticaPerfil.exigirTitular(id, autenticado);

        Perfil perfil = perfiles.porId(id).orElseThrow(() -> new PerfilNoEncontradoException(id));

        // El dominio valida forma y deduplica; que las tecnologias existan en el
        // catalogo necesita la base, asi que se comprueba aqui.
        perfil.actualizar(nombre, biografia, tecnologias, githubUrl, linkedinUrl);

        Set<Short> ids = perfil.idsDeTecnologias();
        if (!perfiles.tecnologiasValidas(ids)) {
            throw new ExcepcionNegocio(
                    CodigoErrorPerfil.PERFIL_TECNOLOGIA_DESCONOCIDA,
                    CodigoErrorPerfil.PERFIL_TECNOLOGIA_DESCONOCIDA.mensajePorDefecto(),
                    List.of(DetalleError.de("tecnologias", ids, "no_aprobadas_o_inexistentes")));
        }

        Perfil guardado = perfiles.guardar(perfil);
        return PerfilDetalle.de(guardado, perfiles.nombresDeTecnologias(guardado.idsDeTecnologias()));
    }
}