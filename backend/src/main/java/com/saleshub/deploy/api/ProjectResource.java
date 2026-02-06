package com.saleshub.deploy.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saleshub.deploy.api.dto.ProjectDtos;
import com.saleshub.deploy.domain.ProjectEntity;
import com.saleshub.deploy.service.ClockProvider;
import com.saleshub.deploy.service.DeploymentEngineService;
import com.saleshub.deploy.service.GitHubService;
import com.saleshub.deploy.service.IdGenerator;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    public Response createProject(ProjectDtos.CreateProjectRequest request) {
        ProjectEntity project = new ProjectEntity();
        project.id = idGenerator.newId();
        project.orgId = request.orgId;
        project.name = request.name;
        project.slug = request.slug;
        project.sourceType = ProjectEntity.SourceType.valueOf(request.sourceType);
        project.repoUrl = request.repoUrl;
        // Support both githubOwner/githubRepo and repoOwner/repoName
        project.githubOwner = request.githubOwner != null ? request.githubOwner : request.repoOwner;
        project.githubRepo = request.githubRepo != null ? request.githubRepo : request.repoName;
        project.defaultBranch = request.defaultBranch != null ? request.defaultBranch : "main";
        project.buildCommand = request.buildCommand;
        project.outputDirectory = request.outputDirectory;
        project.pluginName = request.pluginName;
        project.pluginUserId = request.pluginUserId;
        project.createdAt = clock.nowUtc();
        project.updatedAt = project.createdAt;
        // Persistence removed for MVP

        String defaultDomain = project.slug + "-" + project.id.substring(0, 6) + ".yourplatform.dev";

        // Create Vercel project
        try {
            Map<String, Object> vercelBody = new java.util.HashMap<>();
            vercelBody.put("name", project.slug);
            if (project.sourceType == ProjectEntity.SourceType.GITHUB && project.githubOwner != null && project.githubRepo != null) {
                vercelBody.put("gitRepository", Map.of(
                    "type", "github",
                    "repo", project.githubOwner + "/" + project.githubRepo
                ));
            }
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
                
                // Store repo metadata as env vars for stateless retrieval
                if (project.githubOwner != null && project.githubRepo != null) {
                    storeRepoMetadata(project.vercelProjectId, project.githubOwner, project.githubRepo, project.defaultBranch);
                }
            } else {
                System.err.println("Failed to create Vercel project: " + vercelResponse.statusCode() + " " + vercelResponse.body());
                // Fallback: try to fetch if it already exists?
                // For MVP, proceed.
            }
        } catch (Exception e) {
            System.err.println("Error creating Vercel project: " + e.getMessage());
            e.printStackTrace();
        }

        // Auto-create repo if requested and not provided
        if (project.sourceType == ProjectEntity.SourceType.GITHUB && request.autoCreateRepo) {
            gitHubService.createRepositoryForProject(project, request.repoPrivate);
        }

        String latestDeploymentId = null;
        if (project.sourceType == ProjectEntity.SourceType.GITHUB) {
            try {
                // Trigger safe MVP deployment of latest branch
                var deployment = deploymentEngineService.triggerGitHubDeployment(project, null, project.defaultBranch);
                latestDeploymentId = deployment.id;
            } catch (Exception e) {
                System.err.println("Failed to trigger initial deployment: " + e.getMessage());
            }
        }

        ProjectDtos.ProjectResponse response = ProjectDtos.ProjectResponse.fromEntity(project);
        response.defaultDomain = defaultDomain;
        response.latestDeploymentId = latestDeploymentId;
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @Inject
    com.saleshub.deploy.service.DomainService domainService;

    public static class AttachDomainRequest {
        public String hostname;
    }

    public static class ChangeAliasRequest {
        public String alias;
    }

    public static class DeploymentTriggerRequest {
        public String githubOwner;
        public String githubRepo;
        public String branch;
    }

    /**
     * Trigger a new deployment for an existing project by slug.
     */
    @Path("/{slug}/deploy")
    @POST
    public Response triggerDeploy(@PathParam("slug") String slug, DeploymentTriggerRequest request) {
        // Fetch project state from Vercel to allow stateless operation
        ProjectEntity project = deploymentEngineService.fetchProjectSettings(slug);
        
        if (project == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Project not found: " + slug).build();
        }

        // Apply overrides/fallbacks if Vercel Link is missing
        if (request != null) {
            if (request.githubOwner != null) project.githubOwner = request.githubOwner;
            if (request.githubRepo != null) project.githubRepo = request.githubRepo;
            if (request.branch != null) project.defaultBranch = request.branch;
        }

        // Validate we have the minimum required information for deployment
        if (project.githubOwner == null || project.githubRepo == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Project is not linked to a GitHub repository. Please provide 'githubOwner', 'githubRepo', and 'branch' in the request body.")
                .build();
        }

        // Ensure we have a branch (default to main if not specified)
        if (project.defaultBranch == null || project.defaultBranch.isEmpty()) {
            project.defaultBranch = "main";
        }

        // Set source type and fetch repo details
        project.sourceType = ProjectEntity.SourceType.GITHUB;
        gitHubService.fillRepoDetails(project);

        // Verify we successfully fetched the repoId
        if (project.githubRepoId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Failed to fetch repository details from GitHub for " + project.githubOwner + "/" + project.githubRepo + ". Please verify the repository exists and is accessible.")
                .build();
        }

        try {
            var deployment = deploymentEngineService.triggerGitHubDeployment(project, null, project.defaultBranch);
            
            if (deployment.status == com.saleshub.deploy.domain.DeploymentEntity.Status.FAILED) { // Check status
                 return Response.serverError().entity("Deployment failed: " + deployment.logs).build();
            }

            return Response.ok(deployment.vercelDeploymentId).build(); // Return deployment ID as requested
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{slug}/domains")
    public Response attachCustomDomain(@PathParam("slug") String slug, AttachDomainRequest request) {
        ProjectEntity project = deploymentEngineService.fetchProjectSettings(slug);
        if (project == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Project not found").build();
        }

        com.saleshub.deploy.domain.DomainEntity domain = domainService.attachCustomDomain(project, request.hostname);

        // MVP: Return simplified instructions
        return Response.status(Response.Status.CREATED).entity(
            Map.of(
                "hostname", domain.hostname,
                "status", domain.verified ? "VERIFIED" : "PENDING_CNAME",
                "target", "cname.vercel-dns.com",
                "vercelObjectId", domain.vercelDomainId != null ? domain.vercelDomainId : "unknown"
            )
        ).build();
    }

    @POST
    @Path("/{slug}/alias")
    public Response changeAlias(@PathParam("slug") String slug, ChangeAliasRequest request) {
        ProjectEntity project = deploymentEngineService.fetchProjectSettings(slug);
        if (project == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Project not found").build();
        }

        com.saleshub.deploy.domain.DomainEntity domain = domainService.changeAlias(project, request.alias);

        return Response.ok(Map.of(
            "alias", domain.hostname,
            "status", "ATTACHED",
            "vercelObjectId", domain.vercelDomainId != null ? domain.vercelDomainId : "unknown"
        )).build();
    }

    @DELETE
    @Path("/{slug}/domains/{hostname}")
    public Response detachDomain(@PathParam("slug") String slug,
                                 @PathParam("hostname") String hostname) {
        ProjectEntity project = deploymentEngineService.fetchProjectSettings(slug);
        if (project == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Project not found").build();
        }

        domainService.detachDomain(project.vercelProjectId, hostname);
        return Response.noContent().build();
    }

    /**
     * Store GitHub repo metadata as Vercel environment variables for stateless retrieval.
     */
    private void storeRepoMetadata(String vercelProjectId, String githubOwner, String githubRepo, String defaultBranch) {
        try {
            Map<String, Object> envVars = Map.of(
                "GITHUB_REPO_OWNER", Map.of("type", "plain", "value", githubOwner, "target", new String[]{"production", "preview", "development"}),
                "GITHUB_REPO_NAME", Map.of("type", "plain", "value", githubRepo, "target", new String[]{"production", "preview", "development"}),
                "GITHUB_DEFAULT_BRANCH", Map.of("type", "plain", "value", defaultBranch, "target", new String[]{"production", "preview", "development"})
            );

            String jsonBody = objectMapper.writeValueAsString(envVars);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vercelBaseUrl + "/v10/projects/" + vercelProjectId + "/env"))
                    .header("Authorization", "Bearer " + vercelApiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("Failed to store repo metadata: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Error storing repo metadata: " + e.getMessage());
        }
    }
}

