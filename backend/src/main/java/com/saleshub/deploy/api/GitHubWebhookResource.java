package com.saleshub.deploy.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/integrations/github/webhook/{projectId}")
public class GitHubWebhookResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWebhook(@PathParam("projectId") String projectId, String payload) {
        String deploymentId = "dep_" + projectId.substring(0, 6);

        return Response.ok(
            Map.of(
                "message", "Deployment triggered",
                "projectId", projectId,
                "deploymentId", deploymentId,
                "status", "DEPLOYING"
            )
        ).build();
    }
}

