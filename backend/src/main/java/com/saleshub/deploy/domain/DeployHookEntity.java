package com.saleshub.deploy.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "deploy_hooks")
public class DeployHookEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public String id;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Column(name = "hook_id", nullable = false, unique = true)
    public String hookId;

    @Column(name = "description")
    public String description;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "last_triggered_at")
    public OffsetDateTime lastTriggeredAt;
}

