package com.nebula.common.core.context;

import com.nebula.common.core.constant.SecurityConstants;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void emptyContextReturnsDefaults() {
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getUsername());
        assertEquals(List.of(), UserContext.getRoles());
        assertEquals(List.of(), UserContext.getPermissions());
        assertFalse(UserContext.hasRole("admin"));
        assertFalse(UserContext.hasPermission("system:user:list"));
    }

    @Test
    void legacySettersRemainCompatible() {
        UserContext.set(7L, "alice");

        assertEquals(7L, UserContext.getUserId());
        assertEquals("alice", UserContext.getUsername());
        assertEquals(List.of(), UserContext.getRoles());
        assertEquals(List.of(), UserContext.getPermissions());
    }

    @Test
    void normalizesRolesAndPermissions() {
        UserContext.set(9L, "bob",
                List.of(" admin ", "", "editor", "admin"),
                List.of(" blog:post:add ", "blog:post:add", "blog:post:edit"));

        assertEquals(List.of("admin", "editor"), UserContext.getRoles());
        assertEquals(List.of("blog:post:add", "blog:post:edit"), UserContext.getPermissions());
        assertTrue(UserContext.hasRole("admin"));
        assertTrue(UserContext.hasPermission("blog:post:add"));
        assertFalse(UserContext.hasRole("ADMIN"));
        assertFalse(UserContext.hasPermission("BLOG:POST:ADD"));
        assertThrows(UnsupportedOperationException.class, () -> UserContext.getRoles().add("guest"));
    }

    @Test
    void wildcardGrantsAllChecks() {
        UserContext.set(1L, null, List.of("*"), List.of("*"));

        assertTrue(UserContext.hasRole("any-role"));
        assertTrue(UserContext.hasPermission("any:permission"));
    }

    @Test
    void superAdminRoleGrantsAllChecks() {
        UserContext.set(1L, null, List.of(SecurityConstants.ROLE_SUPER_ADMIN), List.of());

        assertTrue(UserContext.hasRole("any-role"));
        assertTrue(UserContext.hasPermission("any:permission"));
    }

    @Test
    void clearRemovesContext() {
        UserContext.set(1L, "alice", List.of("admin"), List.of("blog:post:add"));
        UserContext.clear();

        assertNull(UserContext.get());
        assertEquals(List.of(), UserContext.getRoles());
        assertEquals(List.of(), UserContext.getPermissions());
    }
}
