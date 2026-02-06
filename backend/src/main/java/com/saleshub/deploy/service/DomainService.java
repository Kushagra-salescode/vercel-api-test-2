package com.saleshub.deploy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saleshub.deploy.domain.DomainEntity;
import com.saleshub.deploy.domain.ProjectEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;

@ApplicationScoped
public class DomainService {

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

    public DomainEntity attachCustomDomain(ProjectEntity project, String hostname) {
        return attachDomain(project, hostname, DomainEntity.Type.CUSTOM);
    }

    public DomainEntity changeAlias(ProjectEntity project, String newAlias) {
        // 1. Attach new alias
        DomainEntity domain = attachDomain(project, newAlias, DomainEntity.Type.DEFAULT);
        
        // 2. Remove old aliases (heuristic: remove other .vercel.app domains)
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vercelBaseUrl + "/v9/projects/" + project.vercelProjectId + "/domains"))
                    .header("Authorization", "Bearer " + vercelApiToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                if (json.has("domains")) {
                    for (JsonNode d : json.get("domains")) {
                        String name = d.get("name").asText();
                        if (name.endsWith(".vercel.app") && !name.equals(newAlias)) {
                            detachDomain(project.vercelProjectId, name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.error("Error cleaning up old aliases", e);
        }
        return domain;
    }

    public void detachDomain(String vercelProjectId, String hostname) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vercelBaseUrl + "/v9/projects/" + vercelProjectId + "/domains/" + hostname))
                    .header("Authorization", "Bearer " + vercelApiToken)
                    .DELETE()
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            Log.error("Failed to detach domain in Vercel", e);
        }
    }

    private DomainEntity attachDomain(ProjectEntity project, String hostname, DomainEntity.Type type) {
        DomainEntity domain = new DomainEntity();
        domain.id = idGenerator.newId();
        domain.projectId = project.id;
        domain.hostname = hostname;
        domain.type = type;
        domain.primary = true;
        domain.verified = false;
        domain.createdAt = clock.nowUtc();
        domain.updatedAt = domain.createdAt;
        // Persistence removed for MVP
        
        try {
            Map<String, Object> payload = Map.of(
                    "name", hostname
            );
            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vercelBaseUrl + "/v9/projects/" + project.vercelProjectId + "/domains"))
                    .header("Authorization", "Bearer " + vercelApiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                domain.vercelDomainId = json.get("id").asText();
                domain.verified = json.get("verified").asBoolean(false);
                domain.updatedAt = clock.nowUtc();
            } else {
                Log.errorf("Failed to attach Vercel domain: %s %s", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            Log.error("Error attaching domain in Vercel", e);
        }

        return domain;
    }
}

