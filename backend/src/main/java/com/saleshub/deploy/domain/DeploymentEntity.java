package com.saleshub.deploy.domain;

import java.time.OffsetDateTime;

public class DeploymentEntity {

    public enum TriggerType {
        GITHUB_PUSH,
        ZIP_UPLOAD,
        MANUAL
    }

    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public String id;
    public String projectId;
    public ProjectEntity.SourceType sourceType;
    public TriggerType triggerType;
    public String commitHash;
    public String branch;
    public Status status;
    public String logs;
    public String zipObjectKey;
    public String vercelDeploymentId;
    public String url;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
