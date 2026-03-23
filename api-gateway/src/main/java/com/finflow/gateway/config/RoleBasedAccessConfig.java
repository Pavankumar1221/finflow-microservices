package com.finflow.gateway.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Centralized Role-Based Access Control (RBAC) configuration.
 *
 * Maps URL path PREFIXES to the list of roles that are ALLOWED to access them.
 * A user must have AT LEAST ONE of the listed roles to be granted access.
 *
 * Roles are stored as comma-separated values inside the JWT claim "roles",
 * e.g.  "ROLE_ADMIN"  or  "ROLE_APPLICANT,ROLE_USER"
 *
 * To add a new rule, simply add a new entry to ROLE_MAP.
 * Order does NOT matter — all entries are checked independently.
 */
@Component
public class RoleBasedAccessConfig {

    /**
     * Key   = URL path prefix (checked with String.startsWith)
     * Value = List of roles that are permitted to access this prefix
     */
    public static final Map<String, List<String>> ROLE_MAP = Map.of(

            // Only admins may call any /admin/** endpoint
            "/admin",          List.of("ROLE_ADMIN"),

            // Applicants and admins can manage loan applications
            "/applications",   List.of("ROLE_APPLICANT", "ROLE_USER", "ROLE_ADMIN"),

            // Any authenticated user can upload/view documents;
            // admins can verify/reject (enforced inside document-service if needed)
            "/documents",      List.of("ROLE_APPLICANT", "ROLE_USER", "ROLE_ADMIN")
    );

    /**
     * Returns true if the given role string contains at least one of the
     * required roles for the given request path.
     *
     * @param path       The incoming request path (e.g. "/admin/applications/5/approve")
     * @param userRoles  Comma-separated roles from the JWT (e.g. "ROLE_APPLICANT")
     * @return true  → access is GRANTED
     *         false → access is DENIED (caller should return 403)
     */
    public boolean isAuthorized(String path, String userRoles) {
        if (userRoles == null || userRoles.isBlank()) return false;

        List<String> rolesFromToken = List.of(userRoles.split(","));

        return ROLE_MAP.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .findFirst()
                .map(entry -> entry.getValue().stream()
                        .anyMatch(rolesFromToken::contains))
                .orElse(true); // If no rule defined for this path, allow (JWT is already valid)
    }
}
