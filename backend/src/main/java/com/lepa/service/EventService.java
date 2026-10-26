package com.lepa.service;

import com.lepa.model.JourneyEvent;
import com.lepa.repository.JourneyEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {
    private final JourneyEventRepository repo;
    private final ApplicationEventPublisher publisher;

    public EventService(JourneyEventRepository repo, ApplicationEventPublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }

    public JourneyEvent emit(Long journeyId, String type, String message) {
        JourneyEvent event = repo.save(new JourneyEvent(journeyId, type, message));
        publisher.publishEvent(new LepaDomainEvent(journeyId, type, message, event.getOccurredAt()));
        return event;
    }

    public List<JourneyEvent> recent() {
        return repo.findTop20ByOrderByOccurredAtDesc();
    }
}
