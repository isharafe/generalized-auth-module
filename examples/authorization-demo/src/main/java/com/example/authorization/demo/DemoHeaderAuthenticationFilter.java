package com.example.authorization.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class DemoHeaderAuthenticationFilter extends OncePerRequestFilter {
  static final String DEMO_USER_COOKIE = "DEMO_USER";
  private static final String DEMO_USER_PARAMETER = "demo-user";
  private static final Set<String> USERS = Set.of("viewer", "manager");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestedUser = request.getParameter(DEMO_USER_PARAMETER);
    String user = firstValid(request.getHeader("X-Demo-User"), requestedUser, cookieUser(request));

    if (user != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(user, "N/A", List.of()));
    }
    if (requestedUser != null && USERS.contains(requestedUser)) {
      Cookie cookie = new Cookie(DEMO_USER_COOKIE, requestedUser);
      cookie.setHttpOnly(true);
      cookie.setSecure(request.isSecure());
      cookie.setPath("/");
      cookie.setMaxAge(60 * 60);
      cookie.setAttribute("SameSite", "Lax");
      response.addCookie(cookie);
    }
    chain.doFilter(request, response);
  }

  private String firstValid(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && USERS.contains(candidate)) return candidate;
    }
    return null;
  }

  private String cookieUser(HttpServletRequest request) {
    if (request.getCookies() == null) return null;
    for (Cookie cookie : request.getCookies()) {
      if (DEMO_USER_COOKIE.equals(cookie.getName())) return cookie.getValue();
    }
    return null;
  }
}
