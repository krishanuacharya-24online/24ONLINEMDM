package com.e24online.mdm.web;

import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.records.user.UserResponse;
import com.e24online.mdm.service.UserAdminService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminControllerTest {

    @Mock
    private UserAdminService userAdminService;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private UserAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new UserAdminController(userAdminService, requestContext);
    }

    @Test
    void listUsers_usesHeaderTenantWhenFilterMissing() {
        UserPrincipal principal = new UserPrincipal(10L, "admin", "PRODUCT_ADMIN", null);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(userAdminService.listUsers(principal, "TENANT_USER", "ACTIVE", "tenant-a", 1, 25))
                .thenReturn(Flux.just(new UserResponse(1L, "u1", "TENANT_USER", "ACTIVE", "tenant-a")));

        List<UserResponse> users = controller
                .listUsers("tenant-a", authentication, "TENANT_USER", "ACTIVE", null, 1, 25)
                .collectList()
                .block();

        assertNotNull(users);
        assertEquals(1, users.size());
        verify(userAdminService).listUsers(principal, "TENANT_USER", "ACTIVE", "tenant-a", 1, 25);
    }

    @Test
    void listUsers_prefersExplicitTenantFilter() {
        UserPrincipal principal = new UserPrincipal(10L, "admin", "PRODUCT_ADMIN", null);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(userAdminService.listUsers(principal, "TENANT_USER", "ACTIVE", "tenant-b", 1, 25))
                .thenReturn(Flux.just(new UserResponse(1L, "u1", "TENANT_USER", "ACTIVE", "tenant-b")));

        List<UserResponse> users = controller
                .listUsers("tenant-a", authentication, "TENANT_USER", "ACTIVE", "tenant-b", 1, 25)
                .collectList()
                .block();

        assertNotNull(users);
        assertEquals(1, users.size());
        verify(userAdminService).listUsers(principal, "TENANT_USER", "ACTIVE", "tenant-b", 1, 25);
    }

    @Test
    void getUser_create_update_delete_delegateToService() {
        UserPrincipal principal = new UserPrincipal(11L, "admin", "TENANT_ADMIN", 77L);
        UserResponse response = new UserResponse(2L, "user2", "TENANT_USER", "ACTIVE", "tenant-a");
        UserResponse updatedResponse = new UserResponse(2L, "user2", "TENANT_USER", "INACTIVE", "tenant-a");
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(userAdminService.getUser(2L, principal)).thenReturn(Mono.just(response));
        when(userAdminService.createUser(eq(principal), eq("newuser"), eq("StrongPass1!"), eq("TENANT_USER"), eq("ACTIVE"), eq("tenant-a")))
                .thenReturn(Mono.just(response));
        when(userAdminService.updateUser(2L, principal, "TENANT_USER", "INACTIVE", "tenant-a", "NextStrong1!"))
                .thenReturn(Mono.just(updatedResponse));
        when(userAdminService.deleteUser(2L, principal)).thenReturn(Mono.empty());

        UserResponse getResult = controller.getUser(authentication, 2L).block();
        assertEquals("user2", getResult.username());

        UserAdminController.UserCreateRequest createBody =
                new UserAdminController.UserCreateRequest("newuser", "StrongPass1!", "TENANT_USER", "ACTIVE", "tenant-a");
        UserResponse createResult = controller.createUser(authentication, Mono.just(createBody)).block();
        assertEquals(2L, createResult.id());

        UserAdminController.UserUpdateRequest updateBody =
                new UserAdminController.UserUpdateRequest("TENANT_USER", "INACTIVE", "tenant-a", "NextStrong1!");
        UserResponse updateResult = controller.updateUser(authentication, 2L, Mono.just(updateBody)).block();
        assertEquals("INACTIVE", updateResult.status());

        controller.deleteUser(authentication, 2L).block();

        verify(userAdminService).getUser(2L, principal);
        verify(userAdminService).createUser(eq(principal), eq("newuser"), eq("StrongPass1!"), eq("TENANT_USER"), eq("ACTIVE"), eq("tenant-a"));
        verify(userAdminService).updateUser(2L, principal, "TENANT_USER", "INACTIVE", "tenant-a", "NextStrong1!");
        verify(userAdminService).deleteUser(2L, principal);
        verify(requestContext, atLeastOnce()).requireUserPrincipal(any(Authentication.class));
    }
}
