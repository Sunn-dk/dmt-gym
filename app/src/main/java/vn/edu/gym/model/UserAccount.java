package vn.edu.gym.model;

public record UserAccount(
        long id,
        String username,
        String passwordHash,
        String fullName,
        Role role,
        Long trainerId,
        int credentialVersion,
        boolean active,
        String trainerName
) {
}
