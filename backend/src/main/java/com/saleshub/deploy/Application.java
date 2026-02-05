package com.saleshub.deploy;

/**
 * Base JAX-RS application.
 * Root path is configured via quarkus.http.root-path in application.properties.
 *
 * We intentionally extend the fully qualified JAX-RS Application class to avoid
 * name clashes with this class.
 */
public class Application extends jakarta.ws.rs.core.Application {
}

