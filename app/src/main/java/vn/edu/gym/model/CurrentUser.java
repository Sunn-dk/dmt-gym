package vn.edu.gym.model;

import java.io.Serializable;

/**
 * Authenticated principal metadata stored in SecurityContext / session.
 */
public record CurrentUser(
        long id,
        String username,
        String fullName,
        Role role,
        Long trainerId,
        int credentialVersion
) implements Serializable {
}
