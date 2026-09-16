package com.codefactory.devnet.project.api;

import com.codefactory.devnet.project.api.dto.PublicProjectSummary;
import java.util.List;

/** API que Project ofrece a Profile dentro del monolito modular. */
public interface PublicProjectQuery {
    List<PublicProjectSummary> findPublishedByOwner(Long ownerId);
}
