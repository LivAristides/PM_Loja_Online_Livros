package br.com.livrariaonline.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AppUser(UUID id, String name, String email, String passwordHash, boolean enabled,
                      int failedLoginAttempts, Instant lockedUntil, Set<Role> roles) { }
