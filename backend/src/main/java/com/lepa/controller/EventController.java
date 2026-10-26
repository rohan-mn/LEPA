package com.lepa.controller;

import com.lepa.model.JourneyEvent;
import com.lepa.service.EventService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService service;
    public EventController(EventService service) { this.service = service; }
    @GetMapping("/recent") public List<JourneyEvent> recent() { return service.recent(); }
}
