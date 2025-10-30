package pl.fixpoint.fixpointbackend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
public class TestController {
    @GetMapping("/secure")
    @PreAuthorize("hasAuthority('CUSTOMERS')") // Dodaj tę adnotację
    public ResponseEntity<String> secureEndpoint() {
        return ResponseEntity.ok("SUKCES: Dostęp dla CUSTOMERS/ADMIN");
    }
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> adminEndpoint() {
        return ResponseEntity.ok("SUKCES: Dostęp dla ADMIN");
    }
}
