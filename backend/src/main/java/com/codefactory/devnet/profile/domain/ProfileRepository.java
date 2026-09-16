package com.codefactory.devnet.profile.domain;

import java.util.Optional;

/** Puerto del modulo profile; application no depende de JPA. */
public interface ProfileRepository {
    Optional<Profile> findById(Long id);
    Profile save(Profile profile);
}
