package com.saleshub.deploy.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "domains")
public class DomainEntity extends PanacheEntityBase {

    public enum Type {
        DEFAULT,
        CUSTOM
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public String id;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Column(name = "hostname", nullable = false, unique = true)
    public String hostname;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    public Type type;

    @Column(name = "vercel_domain_id")
    public String vercelDomainId;

    @Column(name = "verified", nullable = false)
    public boolean verified;

    @Column(name = "primary", nullable = false)
    public boolean primary;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}

