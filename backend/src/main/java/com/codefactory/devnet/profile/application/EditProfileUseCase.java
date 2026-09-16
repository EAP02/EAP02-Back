package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.profile.application.command.EditProfileCommand;
import com.codefactory.devnet.profile.domain.Profile;
import com.codefactory.devnet.profile.domain.ProfileNotFoundException;
import com.codefactory.devnet.profile.domain.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditProfileUseCase {
    private final ProfileRepository profileRepository;
    public EditProfileUseCase(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }
    @Transactional
    public Profile execute(EditProfileCommand command) {
        Profile profile = profileRepository.findById(command.profileId()).orElseThrow(() -> new ProfileNotFoundException(command.profileId()));
        profile.update(command.displayName(), command.bio(), command.technologies(), command.githubUrl(), command.linkedinUrl());
        return profileRepository.save(profile);
    }
}
