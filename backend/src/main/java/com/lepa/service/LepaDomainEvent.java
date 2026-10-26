package com.lepa.service;

import java.time.Instant;

/**
 * Lightweight in-process domain event for the Docker-free showcase.
 * It deliberately replaces Kafka at MVP runtime while preserving an event-driven boundary.
 */
public record LepaDomainEvent(Long journeyId, String type, String message, Instant occurredAt) {}
