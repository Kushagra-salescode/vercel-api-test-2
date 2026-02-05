package com.saleshub.deploy.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "webhooks")
public class WebhookEntity extends PanacheEntityBase {

    public enum Provider {
        GITHUB
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public String id;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    public Provider provider;

    @Column(name = "external_id")
    public String externalId;

    @Column(name = "secret", nullable = false)
    public String secret;

    @Column(name = "url", nullable = false)
    public String url;

    @Column(name = "last_event_id")
    public String lastEventId;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}

