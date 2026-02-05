package com.saleshub.deploy.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "projects")
public class ProjectEntity extends PanacheEntityBase {

    public enum SourceType {
        GITHUB,
        ZIP,
        PLUGIN,
        MANUAL
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public String id;

    @Column(name = "org_id", nullable = false)
    public String orgId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "slug", nullable = false)
    public String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    public SourceType sourceType;

    @Column(name = "repo_url")
    public String repoUrl;

    @Column(name = "github_owner")
    public String githubOwner;

    @Column(name = "github_repo")
    public String githubRepo;

    @Column(name = "default_branch")
    public String defaultBranch;

    @Column(name = "build_command")
    public String buildCommand;

    @Column(name = "output_directory")
    public String outputDirectory;

    @Column(name = "vercel_project_id")
    public String vercelProjectId;

    @Column(name = "vercel_team_id")
    public String vercelTeamId;

    @Column(name = "plugin_name")
    public String pluginName;

    @Column(name = "plugin_user_id")
    public String pluginUserId;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}

