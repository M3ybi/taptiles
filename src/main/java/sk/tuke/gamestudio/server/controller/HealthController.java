package sk.tuke.gamestudio.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Collections.singletonMap("ok", true);
    }
}
