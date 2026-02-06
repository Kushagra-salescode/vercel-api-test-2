package com.saleshub.deploy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saleshub.deploy.domain.ProjectEntity;
import com.saleshub.deploy.domain.WebhookEntity;
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
import java.util.Base64;
import java.util.Map;

@ApplicationScoped
public class GitHubService {

    @ConfigProperty(name = "github.api.base-url")
    String githubApiBaseUrl;

    @ConfigProperty(name = "github.app.installation.token")
    String githubInstallationToken;

    @ConfigProperty(name = "github.webhook.secret.pepper")
    String webhookSecretPepper;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    IdGenerator idGenerator;

    @Inject
    ClockProvider clock;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Provision a GitHub webhook for the given project. Creates a WebhookEntity persisted in the DB.
     */
    @Transactional
    public WebhookEntity provisionWebhook(ProjectEntity project, String baseWebhookUrl) {
        try {
            String webhookId = idGenerator.newId();
            String secret = generateWebhookSecret(webhookId, project.id);
            String callbackUrl = baseWebhookUrl + "/integrations/github/webhook/" + project.id;

            String owner = project.githubOwner;
            String repo = project.githubRepo;

            Map<String, Object> body = Map.of(
                    "name", "web",
                    "active", true,
                    "events", new String[]{"push"},
                    "config", Map.of(
                            "url", callbackUrl,
                            "content_type", "json",
                            "secret", secret
                    )
            );

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(githubApiBaseUrl + "/repos/" + owner + "/" + repo + "/hooks"))
                    .header("Authorization", "Bearer " + githubInstallationToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "saleshub-deploy-platform")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                String externalHookId = json.get("id").asText();

                WebhookEntity webhook = new WebhookEntity();
                webhook.id = webhookId;
                webhook.projectId = project.id;
                webhook.provider = WebhookEntity.Provider.GITHUB;
                webhook.externalId = externalHookId;
                webhook.secret = secret;
                webhook.url = callbackUrl;
                webhook.createdAt = clock.nowUtc();
                webhook.updatedAt = webhook.createdAt;
                webhook.persist();

                return webhook;
            } else {
                Log.errorf("Failed to create GitHub webhook: %s %s", response.statusCode(), response.body());
                throw new IllegalStateException("GitHub webhook provisioning failed");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error provisioning GitHub webhook", e);
        }
    }

    /**
     * Optionally create a new GitHub repo and push a starter template.
     * Here we only create the repo and initialize it with a README via GitHub API.
     */
    @Transactional
    public void createRepositoryForProject(ProjectEntity project, boolean isPrivate) {
        try {
            Map<String, Object> body = Map.of(
                    "name", project.slug,
                    "description", "SalesHub project " + project.name,
                    "private", isPrivate,
                    "auto_init", true
            );

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(githubApiBaseUrl + "/user/repos"))
                    .header("Authorization", "Bearer " + githubInstallationToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "saleshub-deploy-platform")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                project.repoUrl = json.get("html_url").asText();
                String fullName = json.get("full_name").asText();
                String[] parts = fullName.split("/");
                project.githubOwner = parts[0];
                project.githubRepo = parts[1];
                project.defaultBranch = "main";
                project.updatedAt = clock.nowUtc();
                project.updatedAt = clock.nowUtc();
                // Persistence removed for MVP
            } else {
                Log.errorf("Failed to create GitHub repo: %s %s", response.statusCode(), response.body());
                throw new IllegalStateException("GitHub repository creation failed");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error creating GitHub repo", e);
        }
    }

    public void fillRepoDetails(ProjectEntity project) {
        if (project.githubOwner == null || project.githubRepo == null) return;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(githubApiBaseUrl + "/repos/" + project.githubOwner + "/" + project.githubRepo))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "saleshub-deploy-platform")
                    .GET();
            
            if (githubInstallationToken != null && !githubInstallationToken.equals("dev-github-token") && !githubInstallationToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + githubInstallationToken);
            }

            HttpRequest request = builder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                project.githubRepoId = json.get("id").asText();
                project.defaultBranch = json.get("default_branch").asText();
            } else {
                 Log.warnf("Failed to fetch GitHub details for %s/%s: %s", project.githubOwner, project.githubRepo, response.statusCode());
            }
        } catch (Exception e) {
            Log.error("Error fetching GitHub repo details", e);
        }
    }

    private String generateWebhookSecret(String webhookId, String projectId) {
        String raw = webhookId + ":" + projectId + ":" + webhookSecretPepper;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}

