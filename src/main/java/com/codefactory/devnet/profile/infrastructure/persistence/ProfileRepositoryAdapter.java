package com.codefactory.devnet.profile.infrastructure.persistence;

import com.codefactory.devnet.profile.domain.Profile;
import com.codefactory.devnet.profile.domain.ProfileRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class ProfileRepositoryAdapter implements ProfileRepository {
    private final SpringDataProfileRepository repository;
    public ProfileRepositoryAdapter(SpringDataProfileRepository repository) { this.repository = repository; }
    @Override public Optional<Profile> findById(Long id) { return repository.findById(id).map(this::toDomain); }
    @Override public Profile save(Profile profile) { return toDomain(repository.save(toEntity(profile))); }
    private Profile toDomain(ProfileJpaEntity entity) {
        return new Profile(entity.getId(), entity.getDisplayName(), entity.getBio(), entity.getAvatarUrl(),
                entity.getTechnologies(), entity.getGithubUrl(), entity.getLinkedinUrl());
    }
    private ProfileJpaEntity toEntity(Profile profile) {
        return new ProfileJpaEntity(profile.getId(), profile.getDisplayName(), profile.getBio(), profile.getAvatarUrl(),
                profile.getTechnologies(), profile.getGithubUrl(), profile.getLinkedinUrl());
    }
}
