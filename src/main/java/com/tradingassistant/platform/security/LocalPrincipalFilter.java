package com.tradingassistant.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Single-owner principal for the local deployment. There is no sign-in flow;
 * every request is executed as the same fixed owner so watchlists, settings and
 * notifications keep working without accounts.
 */
@Component
public class LocalPrincipalFilter extends OncePerRequestFilter {

    public static final String OWNER_ID = "local-owner";
    private static final AuthenticatedUser LOCAL_USER = AuthenticatedUser.of(
            OWNER_ID, "owner@localhost", List.of("USER"));

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(LOCAL_USER, null,
                            LOCAL_USER.roles().stream()
                                    .map(role -> new SimpleGrantedAuthority(role.authority()))
                                    .toList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            context.setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}