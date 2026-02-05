package com.saleshub.deploy.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class IdGenerator {

    public String newId() {
        return UUID.randomUUID().toString();
    }
}

