package com.saleshub.deploy.api.dto;

import com.saleshub.deploy.domain.ProjectEntity;

public class ProjectDtos {

    public static class CreateProjectRequest {
        public String orgId;
        public String name;
        public String slug;
        public String sourceType; // GITHUB, ZIP, PLUGIN, MANUAL
        public String repoUrl;
        public String githubOwner;
        public String githubRepo;
        public String defaultBranch;
        public String buildCommand;
        public String outputDirectory;
        public String pluginName;
        public String pluginUserId;
        public boolean autoCreateRepo;
        public boolean repoPrivate;
    }

    public static class ProjectResponse {
        public String id;
        public String orgId;
        public String name;
        public String slug;
        public String sourceType;
        public String repoUrl;
        public String defaultBranch;
        public String defaultDomain;

        public static ProjectResponse fromEntity(ProjectEntity entity) {
            ProjectResponse r = new ProjectResponse();
            r.id = entity.id;
            r.orgId = entity.orgId;
            r.name = entity.name;
            r.slug = entity.slug;
            r.sourceType = entity.sourceType.name();
            r.repoUrl = entity.repoUrl;
            r.defaultBranch = entity.defaultBranch;
            return r;
        }
    }
}

