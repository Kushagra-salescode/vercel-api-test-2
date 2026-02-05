package com.saleshub.deploy.api;

import com.saleshub.deploy.domain.DomainEntity;
import com.saleshub.deploy.service.DomainService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/projects/{projectId}/domains")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DomainResource {

    public static class AttachDomainRequest {
        public String hostname;
    }

    @Inject
    DomainService domainService;

    @POST
    public Response attachCustomDomain(@PathParam("projectId") String projectId, AttachDomainRequest request) {
        // MVP: no DB
        String fakeSlug = "demo-project";
        String fakeId = projectId.substring(0, 6);
        String target = fakeSlug + "-" + fakeId + ".yourplatform.dev";

        return Response.status(Response.Status.CREATED).entity(
            Map.of(
                "hostname", request.hostname,
                "status", "PENDING_CNAME",
                "target", target
            )
        ).build();
    }

    @DELETE
    @Path("/{domainId}")
    @Transactional
    public Response detachDomain(@PathParam("projectId") String projectId,
                                 @PathParam("domainId") String domainId) {
        DomainEntity domain = DomainEntity.find("id = ?1 and projectId = ?2", domainId, projectId).firstResult();
        if (domain == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        domainService.detachDomain(domain);
        return Response.noContent().build();
    }
}

