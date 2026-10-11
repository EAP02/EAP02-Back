package com.codefactory.devnet.project.api;

import com.codefactory.devnet.project.application.ListarTecnologias;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HU-09.2: catalogo de tecnologias para filtrar la busqueda de proyectos.
 *
 * <p>Vive en el modulo {@code project} porque es quien posee el catalogo
 * ({@code TecnologiaEntity}) y quien lo usa para etiquetar y filtrar. Si en el futuro
 * otro modulo necesita el catalogo, lo consumira a traves de {@code project.api}.</p>
 *
 * <p>Ruta publica: ya estaba abierta en {@code SeguridadConfig} para
 * {@code GET /api/v1/tecnologias/**}, a la espera de este controlador.</p>
 */
@RestController
@RequestMapping("/api/v1/tecnologias")
@Tag(name = "Tecnologias", description = "Catalogo curado de tecnologias")
public class TecnologiaController {

    private final ListarTecnologias listar;

    public TecnologiaController(ListarTecnologias listar) {
        this.listar = listar;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(
            summary = "Listar el catalogo de tecnologias",
            description = """
                    Las tecnologias aprobadas, por categoria y nombre.

                    - `slug` es el valor del filtro `tecnologia` en `GET /api/v1/proyectos`.
                    - `id` es el valor que se envia en `tecnologias` al publicar o editar
                      un proyecto.
                    """)
    public List<ListarTecnologias.TecnologiaCatalogo> listar() {
        return listar.ejecutar();
    }
}
