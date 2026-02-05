package com.saleshub.deploy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saleshub.deploy.domain.DomainEntity;
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
import java.util.Map;

@ApplicationScoped
public class DomainService {

    @ConfigProperty(name = "vercel.api.base-url")
    String vercelBaseUrl;

    @ConfigProperty(name = "vercel.api.token")
    String vercelApiToken;

    @ConfigProperty(name = "vercel.team.id")
    String vercelTeamId;

    @ConfigProperty(name = "deployment.default.domain-suffix")
    String defaultDomainSuffix;

    @ConfigProperty(name = "deployment.plugin.domain-suffix")
    String pluginDomainSuffix;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    IdGenerator idGenerator;

    @Inject
    ClockProvider clock;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Transactional
    public DomainEntity provisionDefaultDomain(ProjectEntity project) {
        String hostname = project.id + "." + defaultDomainSuffix;
        return attachDomain(project, hostname, DomainEntity.Type.DEFAULT);
    }

    @Transactional
    public DomainEntity provisionPluginDomain(ProjectEntity project) {
        if (project.pluginName == null || project.pluginUserId == null) {
            throw new IllegalStateException("Plugin project must have pluginName and pluginUserId");
        }
        String hostname = project.pluginName + "-" + project.pluginUserId + "." + pluginDomainSuffix;
        return attachDomain(project, hostname, DomainEntity.Type.DEFAULT);
    }

    @Transactional
    public DomainEntity attachCustomDomain(ProjectEntity project, String hostname) {
        return attachDomain(project, hostname, DomainEntity.Type.CUSTOM);
    }

    @Transactional
    public void detachDomain(DomainEntity domain) {
        try {
            if (domain.vercelDomainId != null) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(vercelBaseUrl + "/v9/projects/" + domain.projectId + "/domains/" + domain.hostname + "?teamId=" + vercelTeamId))
                        .header("Authorization", "Bearer " + vercelApiToken)
                        .DELETE()
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            }
        } catch (Exception e) {
            Log.error("Failed to detach domain in Vercel", e);
        }
        domain.delete();
    }

    private DomainEntity attachDomain(ProjectEntity project, String hostname, DomainEntity.Type type) {
        DomainEntity existing = DomainEntity.find("hostname", hostname).firstResult();
        if (existing != null) {
            return existing;
        }

        DomainEntity domain = new DomainEntity();
        domain.id = idGenerator.newId();
        domain.projectId = project.id;
        domain.hostname = hostname;
        domain.type = type;
        domain.primary = true;
        domain.verified = false;
        domain.createdAt = clock.nowUtc();
        domain.updatedAt = domain.createdAt;
        domain.persist();

        try {
            Map<String, Object> payload = Map.of(
                    "name", hostname
            );
            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vercelBaseUrl + "/v9/projects/" + project.vercelProjectId + "/domains?teamId=" + vercelTeamId))
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
                domain.persist();
            } else {
                Log.errorf("Failed to attach Vercel domain: %s %s", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            Log.error("Error attaching domain in Vercel", e);
        }

        return domain;
    }
}

