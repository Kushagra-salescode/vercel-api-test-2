package com.saleshub.deploy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saleshub.deploy.domain.DeploymentEntity;
import com.saleshub.deploy.domain.ProjectEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class DeploymentEngineService {

    @ConfigProperty(name = "vercel.api.base-url")
    String vercelBaseUrl;

    @ConfigProperty(name = "vercel.api.token")
    String vercelApiToken;

    @ConfigProperty(name = "vercel.team.id")
    String vercelTeamId;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    IdGenerator idGenerator;

    @Inject
    ClockProvider clock;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // @Transactional // Disable transaction for MVP as we don't use DB
    public DeploymentEntity triggerGitHubDeployment(ProjectEntity project, String commitHash, String branch) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.id = idGenerator.newId();
        deployment.projectId = project.id;
        deployment.sourceType = project.sourceType;
        deployment.triggerType = DeploymentEntity.TriggerType.GITHUB_PUSH;
        deployment.commitHash = commitHash;
        deployment.branch = branch;
        deployment.status = DeploymentEntity.Status.QUEUED;
        deployment.createdAt = clock.nowUtc();
        deployment.updatedAt = deployment.createdAt;
        // deployment.persist(); // Disable for MVP

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", project.slug);
            payload.put("gitSource", new HashMap<String, String>() {{
                put("type", "github");
                put("repo", project.githubOwner + "/" + project.githubRepo);
                put("ref", branch);
                if (commitHash != null) {
                    put("sha", commitHash);
                }
            }});
            if (project.outputDirectory != null) {
                payload.put("outputDirectory", project.outputDirectory);
            }
            if (project.buildCommand != null) {
                payload.put("buildCommand", project.buildCommand);
            }

            String jsonBody = objectMapper.writeValueAsString(payload);
            
            System.out.println("Triggering Vercel deployment for " + project.slug);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vercelBaseUrl + "/v13/deployments?teamId=" + vercelTeamId))
                    .header("Authorization", "Bearer " + vercelApiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Vercel response: " + response.statusCode() + " " + response.body());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                deployment.vercelDeploymentId = json.get("id").asText();
                deployment.url = json.get("url").asText();
                deployment.status = DeploymentEntity.Status.RUNNING;
                deployment.updatedAt = clock.nowUtc();
                // deployment.persist();
            } else {
                deployment.status = DeploymentEntity.Status.FAILED;
                deployment.logs = "Failed to trigger Vercel deployment: " + response.statusCode() + " " + response.body();
                deployment.updatedAt = clock.nowUtc();
                // deployment.persist();
            }
        } catch (Exception e) {
            Log.error("Error triggering Vercel deployment", e);
            deployment.status = DeploymentEntity.Status.FAILED;
            deployment.logs = "Exception triggering Vercel deployment: " + e.getMessage();
            deployment.updatedAt = clock.nowUtc();
            // deployment.persist();
        }

        return deployment;
    }

    /**
     * Trigger a deployment based on an uploaded ZIP artifact.
     * For a real implementation, you would upload files to Vercel using their file-based deployment API.
     * Here we record a deployment and mark it as RUNNING to be picked up by a worker/poller.
     */
    // @Transactional
    public DeploymentEntity triggerZipDeployment(ProjectEntity project, String zipObjectKey) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.id = idGenerator.newId();
        deployment.projectId = project.id;
        deployment.sourceType = project.sourceType;
        deployment.triggerType = DeploymentEntity.TriggerType.ZIP_UPLOAD;
        deployment.zipObjectKey = zipObjectKey;
        deployment.status = DeploymentEntity.Status.QUEUED;
        deployment.createdAt = clock.nowUtc();
        deployment.updatedAt = deployment.createdAt;
        // deployment.persist();
        return deployment;
    }

    // @Transactional
    public void pollAndUpdateDeploymentStatus(DeploymentEntity deployment) {
        if (deployment.vercelDeploymentId == null) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vercelBaseUrl + "/v13/deployments/" + deployment.vercelDeploymentId + "?teamId=" + vercelTeamId))
                    .header("Authorization", "Bearer " + vercelApiToken)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                String state = json.get("readyState").asText();
                deployment.status = mapVercelState(state);
                deployment.url = json.has("url") ? json.get("url").asText() : deployment.url;
                deployment.updatedAt = OffsetDateTime.now();
                // deployment.persist();
            } else {
                Log.warnf("Failed to poll Vercel deployment: %s %s", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            Log.error("Error polling Vercel deployment", e);
        }
    }

    private DeploymentEntity.Status mapVercelState(String state) {
        return switch (state) {
            case "QUEUED", "BUILDING", "INITIALIZING" -> DeploymentEntity.Status.RUNNING;
            case "READY" -> DeploymentEntity.Status.SUCCEEDED;
            case "CANCELED", "ERROR" -> DeploymentEntity.Status.FAILED;
            default -> DeploymentEntity.Status.RUNNING;
        };
    }
}

