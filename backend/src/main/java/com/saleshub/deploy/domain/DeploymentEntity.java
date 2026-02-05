package com.saleshub.deploy.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "deployments")
public class DeploymentEntity extends PanacheEntityBase {

    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public enum TriggerType {
        GITHUB_PUSH,
        ZIP_UPLOAD,
        MANUAL_HOOK,
        PLUGIN_ACTIVATION
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public String id;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    public ProjectEntity.SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    public TriggerType triggerType;

    @Column(name = "commit_hash")
    public String commitHash;

    @Column(name = "branch")
    public String branch;

    @Column(name = "zip_object_key")
    public String zipObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public Status status;

    @Column(name = "vercel_deployment_id")
    public String vercelDeploymentId;

    @Column(name = "url")
    public String url;

    @Column(name = "logs", columnDefinition = "text")
    public String logs;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}

