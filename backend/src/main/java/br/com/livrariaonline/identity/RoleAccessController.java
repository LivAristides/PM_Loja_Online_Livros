package br.com.livrariaonline.identity;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/access")
public class RoleAccessController {
    @GetMapping("/admin/check")
    public Map<String, String> admin() { return Map.of("access", "admin"); }
    @GetMapping("/catalog/check")
    public Map<String, String> catalog() { return Map.of("access", "catalog"); }
    @GetMapping("/finance/check")
    public Map<String, String> finance() { return Map.of("access", "finance"); }
    @GetMapping("/support/check")
    public Map<String, String> support() { return Map.of("access", "support"); }
}
