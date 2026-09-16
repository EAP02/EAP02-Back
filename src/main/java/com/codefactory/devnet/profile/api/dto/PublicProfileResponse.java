package com.codefactory.devnet.profile.api.dto;

import com.codefactory.devnet.project.api.dto.PublicProjectSummary;
import java.util.List;

/** No incluye correo, id de identidad ni mensajes privados. */
public record PublicProfileResponse(Long id, String displayName, String bio, String avatarUrl,
                                    List<String> technologies, String githubUrl, String linkedinUrl,
                                    List<PublicProjectSummary> publishedProjects) { }
