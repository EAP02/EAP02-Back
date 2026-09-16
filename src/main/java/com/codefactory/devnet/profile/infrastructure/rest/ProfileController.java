package com.codefactory.devnet.profile.infrastructure.rest;

import com.codefactory.devnet.profile.api.PublicProfileQuery;
import com.codefactory.devnet.profile.api.dto.PublicProfileResponse;
import com.codefactory.devnet.profile.application.EditAvatarUseCase;
import com.codefactory.devnet.profile.application.EditProfileUseCase;
import com.codefactory.devnet.profile.application.command.EditAvatarCommand;
import com.codefactory.devnet.profile.application.command.EditProfileCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    private final EditProfileUseCase editProfileUseCase;
    private final EditAvatarUseCase editAvatarUseCase;
    private final PublicProfileQuery publicProfileQuery;

    public ProfileController(EditProfileUseCase editProfileUseCase, EditAvatarUseCase editAvatarUseCase,
                             PublicProfileQuery publicProfileQuery) {
        this.editProfileUseCase = editProfileUseCase;
        this.editAvatarUseCase = editAvatarUseCase;
        this.publicProfileQuery = publicProfileQuery;
    }

    @PutMapping("/{profileId}")
    public ResponseEntity<ProfileResponse> editProfile(@PathVariable Long profileId, @Valid @RequestBody EditProfileRequest request) {
        var updated = editProfileUseCase.execute(new EditProfileCommand(profileId, request.displayName(), request.bio(),
                request.technologies(), request.githubUrl(), request.linkedinUrl()));
        return ResponseEntity.ok(ProfileResponse.from(updated));
    }

    @PatchMapping("/{profileId}/avatar")
    public ResponseEntity<ProfileResponse> editAvatar(@PathVariable Long profileId, @Valid @RequestBody EditAvatarRequest request) {
        return ResponseEntity.ok(ProfileResponse.from(editAvatarUseCase.execute(new EditAvatarCommand(profileId, request.avatarUrl()))));
    }

    @GetMapping("/{profileId}")
    public ResponseEntity<PublicProfileResponse> getPublicProfile(@PathVariable Long profileId) {
        return ResponseEntity.ok(publicProfileQuery.getPublicProfile(profileId));
    }
}
