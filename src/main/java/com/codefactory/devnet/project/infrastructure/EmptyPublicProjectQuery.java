package com.codefactory.devnet.project.infrastructure;

import com.codefactory.devnet.project.api.PublicProjectQuery;
import com.codefactory.devnet.project.api.dto.PublicProjectSummary;
import org.springframework.stereotype.Component;
import java.util.List;

/** Temporal: se reemplaza al implementar las publicaciones del modulo project. */
@Component
public class EmptyPublicProjectQuery implements PublicProjectQuery {
    @Override public List<PublicProjectSummary> findPublishedByOwner(Long ownerId) { return List.of(); }
}
