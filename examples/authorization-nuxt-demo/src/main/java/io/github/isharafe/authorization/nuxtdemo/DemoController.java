package io.github.isharafe.authorization.nuxtdemo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {
  private final Map<Long, Employee> employees = new ConcurrentHashMap<>();

  public DemoController() {
    employees.put(1L, new Employee(1, "Ada Lovelace", "Engineering"));
    employees.put(2L, new Employee(2, "Grace Hopper", "Operations"));
  }

  @GetMapping("/public")
  public Map<String, String> publicEndpoint() {
    return Map.of("message", "Nuxt authorization demo backend is available");
  }

  @GetMapping("/profile")
  public Map<String, String> profile(Authentication authentication) {
    return Map.of("user", displayName(authentication));
  }

  @GetMapping("/employees")
  public List<Employee> employees() {
    return employees.values().stream().sorted(Comparator.comparing(Employee::id)).toList();
  }

  @PutMapping("/employees/{id}")
  public Employee update(@PathVariable long id, @RequestBody EmployeeUpdate request) {
    Employee current = employees.get(id);
    if (current == null) throw new EmployeeNotFoundException(id);
    Employee updated = new Employee(id, request.name(), current.department());
    employees.put(id, updated);
    return updated;
  }

  public record Employee(long id, String name, String department) {}

  public record EmployeeUpdate(String name) {
    public EmployeeUpdate {
      if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
      name = name.trim();
    }
  }

  private String displayName(Authentication authentication) {
    if (authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
      Object preferredUsername = principal.getAttribute("preferred_username");
      if (preferredUsername != null) return String.valueOf(preferredUsername);
    }
    return authentication.getName();
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  private static final class EmployeeNotFoundException extends RuntimeException {
    private EmployeeNotFoundException(long id) {
      super("Employee " + id + " was not found");
    }
  }
}
