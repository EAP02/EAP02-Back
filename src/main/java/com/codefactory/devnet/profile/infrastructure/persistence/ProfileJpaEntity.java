package com.codefactory.devnet.profile.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "profiles")
public class ProfileJpaEntity {
    @Id private Long id; // Mismo id del usuario que crea Identity.
    @Column(nullable = false, length = 80) private String displayName;
    @Column(nullable = false, length = 1000) private String bio;
    @Column(length = 500) private String avatarUrl;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_technologies", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "technology", nullable = false, length = 80)
    private List<String> technologies = new ArrayList<>();
    @Column(length = 300) private String githubUrl;
    @Column(length = 300) private String linkedinUrl;

    protected ProfileJpaEntity() { }
    public ProfileJpaEntity(Long id, String displayName, String bio, String avatarUrl, List<String> technologies,
                            String githubUrl, String linkedinUrl) {
        this.id = id; this.displayName = displayName; this.bio = bio; this.avatarUrl = avatarUrl;
        this.technologies = new ArrayList<>(technologies); this.githubUrl = githubUrl; this.linkedinUrl = linkedinUrl;
    }
    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getAvatarUrl() { return avatarUrl; }
    public List<String> getTechnologies() { return technologies; }
    public String getGithubUrl() { return githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
}
