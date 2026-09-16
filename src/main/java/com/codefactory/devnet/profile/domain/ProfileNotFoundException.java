package com.codefactory.devnet.profile.domain;

public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException(Long profileId) { super("No existe un perfil con id " + profileId + "."); }
}
