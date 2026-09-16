package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.profile.application.command.EditAvatarCommand;
import com.codefactory.devnet.profile.domain.Profile;
import com.codefactory.devnet.profile.domain.ProfileNotFoundException;
import com.codefactory.devnet.profile.domain.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditAvatarUseCase {
    private final ProfileRepository profileRepository;
    public EditAvatarUseCase(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository; }
    @Transactional
    public Profile execute(EditAvatarCommand command) {
        Profile profile = profileRepository.findById(command.profileId()).orElseThrow(() -> new ProfileNotFoundException(command.profileId()));
        profile.updateAvatar(command.avatarUrl());
        return profileRepository.save(profile);
    }
}
