package com.codefactory.devnet.profile.application.command;

import java.util.List;

public record EditProfileCommand(Long profileId, String displayName, String bio, List<String> technologies,
                                 String githubUrl, String linkedinUrl) { }
