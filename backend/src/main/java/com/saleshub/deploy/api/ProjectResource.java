package com.saleshub.deploy.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

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

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "vercel.api.base-url")
    String vercelBaseUrl;

    @ConfigProperty(name = "vercel.api.token")
    String vercelApiToken;

    private final HttpClient httpClient = HttpClient.newHttpClient();

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

        // Create Vercel project
        try {
            Map<String, String> vercelBody = Map.of("name", project.slug);
            String jsonBody = objectMapper.writeValueAsString(vercelBody);

            HttpRequest vercelRequest = HttpRequest.newBuilder()
                    .uri(URI.create(vercelBaseUrl + "/v9/projects"))
                    .header("Authorization", "Bearer " + vercelApiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> vercelResponse = httpClient.send(vercelRequest, HttpResponse.BodyHandlers.ofString());

            if (vercelResponse.statusCode() >= 200 && vercelResponse.statusCode() < 300) {
                JsonNode vercelJson = objectMapper.readTree(vercelResponse.body());
                project.vercelProjectId = vercelJson.get("id").asText();
            } else {
                // Log error but don't fail - continue without Vercel project ID
                System.err.println("Failed to create Vercel project: " + vercelResponse.statusCode() + " " + vercelResponse.body());
            }
        } catch (Exception e) {
            // Log error but don't fail - continue without Vercel project ID
            System.err.println("Error creating Vercel project: " + e.getMessage());
            e.printStackTrace();
        }

        // Auto-create repo if requested and not provided
        if (project.sourceType == ProjectEntity.SourceType.GITHUB && request.autoCreateRepo) {
            gitHubService.createRepositoryForProject(project, request.repoPrivate);
        }

        // Provision GitHub webhook when we have a repo
        /*
        if (project.sourceType == ProjectEntity.SourceType.GITHUB && project.githubOwner != null && project.githubRepo != null) {
            // baseWebhookUrl should come from configuration or public URL config; here we assume same host.
            String baseWebhookUrl = "https://api.saleshub-deploy.internal/api";
            gitHubService.provisionWebhook(project, baseWebhookUrl);
        }
        */

        String latestDeploymentId = null;
        if (project.sourceType == ProjectEntity.SourceType.GITHUB) {
            try {
                // Trigger safe MVP deployment of latest branch
                var deployment = deploymentEngineService.triggerGitHubDeployment(project, null, project.defaultBranch);
                latestDeploymentId = deployment.id;
            } catch (Exception e) {
                // Log error but don't fail - continue
                System.err.println("Failed to trigger initial deployment: " + e.getMessage());
            }
        }

        ProjectDtos.ProjectResponse response = ProjectDtos.ProjectResponse.fromEntity(project);
        response.defaultDomain = defaultDomain;
        response.latestDeploymentId = latestDeploymentId;
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
    @Path("/{projectId}/deploy")
    @POST
    // @Transactional
    public Response triggerDeploy(@PathParam("projectId") String projectId) {
        ProjectEntity project = ProjectEntity.findById(projectId);
        if (project == null) {
            // For MVP/Demo without DB, we might not find it if persistence is off.
            return Response.status(Response.Status.NOT_FOUND).entity("Project not found (DB disabled)").build();
        }

        try {
            deploymentEngineService.triggerGitHubDeployment(project, null, project.defaultBranch);
            return Response.ok("Deployment triggered").build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
}

