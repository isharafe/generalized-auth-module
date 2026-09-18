package io.github.isharafe.authorization.demo;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/demo")
public class DemoController {
  @GetMapping("/public")
  public Map<String, String> publicEndpoint() {
    return Map.of("message", "public");
  }

  @GetMapping("/profile")
  public Map<String, String> profile(Principal principal) {
    return Map.of("user", principal.getName());
  }

  @GetMapping("/employees")
  public List<Map<String, Object>> employees() {
    return List.of(Map.of("id", 1, "name", "Ada"));
  }

  @PutMapping("/employees/{id}")
  public Map<String, Object> update(@PathVariable long id) {
    return Map.of("id", id, "updated", true);
  }
}
