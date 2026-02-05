package com.saleshub.deploy.api.dto;

import com.saleshub.deploy.domain.DeploymentEntity;

import java.time.OffsetDateTime;

public class DeploymentDtos {

    public static class DeploymentResponse {
        public String id;
        public String projectId;
        public String status;
        public String triggerType;
        public String commitHash;
        public String branch;
        public String url;
        public OffsetDateTime createdAt;
        public OffsetDateTime updatedAt;

        public static DeploymentResponse fromEntity(DeploymentEntity entity) {
            DeploymentResponse r = new DeploymentResponse();
            r.id = entity.id;
            r.projectId = entity.projectId;
            r.status = entity.status.name();
            r.triggerType = entity.triggerType.name();
            r.commitHash = entity.commitHash;
            r.branch = entity.branch;
            r.url = entity.url;
            r.createdAt = entity.createdAt;
            r.updatedAt = entity.updatedAt;
            return r;
        }
    }
}

