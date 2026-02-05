package com.saleshub.deploy.api;

import com.saleshub.deploy.api.dto.DeploymentDtos;
import com.saleshub.deploy.domain.DeploymentEntity;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;

@Path("/projects/{projectId}/deployments")
@Produces(MediaType.APPLICATION_JSON)
public class DeploymentResource {

    @GET
    public Response listDeployments(@PathParam("projectId") String projectId) {
        List<DeploymentEntity> deployments = DeploymentEntity.list("projectId", projectId);
        List<DeploymentDtos.DeploymentResponse> responses = deployments.stream()
                .map(DeploymentDtos.DeploymentResponse::fromEntity)
                .collect(Collectors.toList());
        return Response.ok(responses).build();
    }

    @GET
    @Path("/{deploymentId}")
    public Response getDeployment(@PathParam("projectId") String projectId,
                                  @PathParam("deploymentId") String deploymentId) {
        DeploymentEntity deployment = DeploymentEntity.find("id = ?1 and projectId = ?2", deploymentId, projectId)
                .firstResult();
        if (deployment == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(DeploymentDtos.DeploymentResponse.fromEntity(deployment)).build();
    }
}

