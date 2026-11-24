package com.example.helloworld.status;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Database status screen (React app served from {@code /status/}) and the JSON
 * API it reads. Turn both off with {@code app.status.enabled=false}.
 *
 * <p>There is no authentication in this app yet, so anyone who can reach it can
 * read this. Put it behind auth before exposing the app publicly.
 */
// TODO(before public release): remove the DB status screen (/status, /api/status/database, frontend/).
@Controller
@ConditionalOnProperty(prefix = "app.status", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseStatusController {

    private final DatabaseStatusService statusService;

    public DatabaseStatusController(DatabaseStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping({"/status", "/status/"})
    public String screen() {
        return "forward:/status/index.html";
    }

    /** 200 when the database is reachable (OK or WARNING), 503 when it is down. */
    @GetMapping("/api/status/database")
    @ResponseBody
    public ResponseEntity<DatabaseStatus> database() {
        DatabaseStatus status = statusService.snapshot();
        HttpStatus code = status.health() == DatabaseStatus.Health.DOWN
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(code).cacheControl(CacheControl.noStore()).body(status);
    }
}
