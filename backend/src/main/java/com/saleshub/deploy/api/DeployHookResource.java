package com.saleshub.deploy.api;

import com.saleshub.deploy.domain.DeployHookEntity;
import com.saleshub.deploy.domain.DeploymentEntity;
import com.saleshub.deploy.domain.ProjectEntity;
import com.saleshub.deploy.service.ClockProvider;
import com.saleshub.deploy.service.DeploymentEngineService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/deploy/{hookId}")
public class DeployHookResource {

    @Inject
    DeploymentEngineService deploymentEngineService;

    @Inject
    ClockProvider clock;

    @POST
    @Transactional
    public Response trigger(@PathParam("hookId") String hookId) {
        DeployHookEntity hook = DeployHookEntity.find("hookId", hookId).firstResult();
        if (hook == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        ProjectEntity project = ProjectEntity.findById(hook.projectId);
        if (project == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        DeploymentEntity deployment = deploymentEngineService.triggerZipDeployment(project, null);
        deployment.triggerType = DeploymentEntity.TriggerType.MANUAL_HOOK;
        deployment.persist();
        hook.lastTriggeredAt = clock.nowUtc();
        hook.persist();
        return Response.accepted().entity(deployment.id).build();
    }
}

