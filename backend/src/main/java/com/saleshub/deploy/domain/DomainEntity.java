package com.saleshub.deploy.domain;

import java.time.OffsetDateTime;

public class DomainEntity {

    public enum Type {
        DEFAULT,
        CUSTOM
    }

    public String id;
    public String projectId;
    public String hostname;
    public Type type;
    public boolean primary;
    public boolean verified;
    public String vercelDomainId;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;

    public void delete() {
        // no-op
    }

    public void persist() {
        // no-op
    }
}
