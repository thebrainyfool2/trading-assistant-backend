package com.tradingassistant.platform.api;

import com.tradingassistant.common.error.GlobalExceptionHandler;
import com.tradingassistant.platform.error.PlatformExceptionHandler;
import com.tradingassistant.platform.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

/**
 * Builds controller slices with the platform's real error advice and security
 * principal resolution.
 */
final class WebTestSupport {

    private WebTestSupport() {
    }

    static MockMvc mvc(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new GlobalExceptionHandler(),
                        new PlatformExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    static void authenticate(AuthenticatedUser user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null,
                        user.roles().stream()
                                .map(role -> new SimpleGrantedAuthority(role.authority()))
                                .toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    static List<String> roleNames(AuthenticatedUser user) {
        return user.roles().stream().map(Enum::name).sorted().toList();
    }
}
