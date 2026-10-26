package com.lepa.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "journey_events")
public class JourneyEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long journeyId;
    private String type;
    @Column(length = 2000)
    private String message;
    private Instant occurredAt = Instant.now();

    public JourneyEvent() {}
    public JourneyEvent(Long journeyId, String type, String message) {
        this.journeyId = journeyId; this.type = type; this.message = message;
    }
    public Long getId() { return id; }
    public Long getJourneyId() { return journeyId; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public Instant getOccurredAt() { return occurredAt; }
}
