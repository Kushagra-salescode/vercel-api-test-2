package com.saleshub.deploy.api;

import com.saleshub.deploy.api.dto.ProjectDtos;
import com.saleshub.deploy.domain.DeployHookEntity;
import com.saleshub.deploy.domain.ProjectEntity;
import com.saleshub.deploy.service.ClockProvider;
import com.saleshub.deploy.service.DeploymentEngineService;
import com.saleshub.deploy.service.GitHubService;
import com.saleshub.deploy.service.IdGenerator;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;

@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    IdGenerator idGenerator;

    @Inject
    ClockProvider clock;

    @Inject
    GitHubService gitHubService;

    @Inject
    DeploymentEngineService deploymentEngineService;

    /**
     * Create a new project. Optionally auto-create a GitHub repo and provision webhooks.
     */
    @POST
    @Transactional
    public Response createProject(ProjectDtos.CreateProjectRequest request) {
        ProjectEntity project = new ProjectEntity();
        project.id = idGenerator.newId();
        project.orgId = request.orgId;
        project.name = request.name;
        project.slug = request.slug;
        project.sourceType = ProjectEntity.SourceType.valueOf(request.sourceType);
        project.repoUrl = request.repoUrl;
        project.githubOwner = request.githubOwner;
        project.githubRepo = request.githubRepo;
        project.defaultBranch = request.defaultBranch != null ? request.defaultBranch : "main";
        project.buildCommand = request.buildCommand;
        project.outputDirectory = request.outputDirectory;
        project.pluginName = request.pluginName;
        project.pluginUserId = request.pluginUserId;
        project.createdAt = clock.nowUtc();
        project.updatedAt = project.createdAt;
        // project.persist();

        String defaultDomain = project.slug + "-" + project.id.substring(0, 6) + ".yourplatform.dev";

        // Auto-create repo if requested and not provided
        if (project.sourceType == ProjectEntity.SourceType.GITHUB && request.autoCreateRepo) {
            gitHubService.createRepositoryForProject(project, request.repoPrivate);
        }

        // Provision GitHub webhook when we have a repo
        if (project.sourceType == ProjectEntity.SourceType.GITHUB && project.githubOwner != null && project.githubRepo != null) {
            // baseWebhookUrl should come from configuration or public URL config; here we assume same host.
            String baseWebhookUrl = "https://api.saleshub-deploy.internal/api";
            gitHubService.provisionWebhook(project, baseWebhookUrl);
        }

        ProjectDtos.ProjectResponse response = ProjectDtos.ProjectResponse.fromEntity(project);
        response.defaultDomain = defaultDomain;
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /**
     * Upload a ZIP archive and create a deployment from it.
     */
    @Path("/{projectId}/upload-zip")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response uploadZip(@PathParam("projectId") String projectId, @RestForm("file") FileUpload fileUpload) {
        ProjectEntity project = ProjectEntity.findById(projectId);
        if (project == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            java.nio.file.Path tempDir = Files.createTempDirectory("deploy-zip-");
            java.nio.file.Path target = tempDir.resolve(fileUpload.fileName());
            Files.move(fileUpload.uploadedFile(), target);

            // In a real implementation, you would upload this to object storage and pass the key.
            String objectKey = target.toAbsolutePath().toString();

            var deployment = deploymentEngineService.triggerZipDeployment(project, objectKey);
            return Response.accepted().entity(deployment.id).build();
        } catch (IOException e) {
            return Response.serverError().entity("Failed to store ZIP: " + e.getMessage()).build();
        }
    }

    /**
     * Create a manual deploy hook for this project.
     */
    @Path("/{projectId}/deploy-hooks")
    @POST
    @Transactional
    public Response createDeployHook(String projectId) {
        ProjectEntity project = ProjectEntity.findById(projectId);
        if (project == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        DeployHookEntity hook = new DeployHookEntity();
        hook.id = idGenerator.newId();
        hook.projectId = project.id;
        hook.hookId = idGenerator.newId();
        hook.createdAt = clock.nowUtc();
        hook.persist();
        return Response.status(Response.Status.CREATED).entity(hook.hookId).build();
    }
}

