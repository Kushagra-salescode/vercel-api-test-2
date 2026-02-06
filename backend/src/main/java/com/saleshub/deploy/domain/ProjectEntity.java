package com.saleshub.deploy.domain;

import java.time.OffsetDateTime;

/**
 * Stateless DTO for Project.
 * Originally an Entity, now just a data carrier.
 */
public class ProjectEntity {

    public enum SourceType {
        GITHUB,
        ZIP,
        PLUGIN,
        MANUAL
    }

    public String id;
    public String orgId;
    public String name;
    public String slug;
    public SourceType sourceType;
    public String repoUrl;
    public String githubOwner;
    public String githubRepo;
    public String githubRepoId;
    public String defaultBranch;
    public String buildCommand;
    public String outputDirectory;
    public String vercelProjectId;
    public String vercelTeamId;
    public String pluginName;
    public String pluginUserId;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    
    // No-op methods for compatibility during refactor if needed, 
    // but better to remove usages.
}
