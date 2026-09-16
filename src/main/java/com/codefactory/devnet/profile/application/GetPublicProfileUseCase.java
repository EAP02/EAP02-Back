package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.profile.api.PublicProfileQuery;
import com.codefactory.devnet.profile.api.dto.PublicProfileResponse;
import com.codefactory.devnet.profile.domain.Profile;
import com.codefactory.devnet.profile.domain.ProfileNotFoundException;
import com.codefactory.devnet.profile.domain.ProfileRepository;
import com.codefactory.devnet.project.api.PublicProjectQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPublicProfileUseCase implements PublicProfileQuery {
    private final ProfileRepository profileRepository;
    private final PublicProjectQuery publicProjectQuery;

    public GetPublicProfileUseCase(ProfileRepository profileRepository, PublicProjectQuery publicProjectQuery) {
        this.profileRepository = profileRepository;
        this.publicProjectQuery = publicProjectQuery;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(Long profileId) {
        Profile profile = profileRepository.findById(profileId).orElseThrow(() -> new ProfileNotFoundException(profileId));
        return new PublicProfileResponse(profile.getId(), profile.getDisplayName(), profile.getBio(), profile.getAvatarUrl(),
                profile.getTechnologies(), profile.getGithubUrl(), profile.getLinkedinUrl(),
                publicProjectQuery.findPublishedByOwner(profileId));
    }
}
