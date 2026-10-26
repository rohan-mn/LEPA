package com.lepa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DomainEventListener {
    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    @EventListener
    public void on(LepaDomainEvent event) {
        log.info("LEPA_EVENT type={} journeyId={} message={}", event.type(), event.journeyId(), event.message());
    }
}
