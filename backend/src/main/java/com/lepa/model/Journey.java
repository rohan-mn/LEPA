package com.lepa.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journeys")
public class Journey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String eventType;
    private String title;
    private String status;
    private String customerName;
    private BigDecimal availableFunds = BigDecimal.ZERO;
    private BigDecimal expectedIncome = BigDecimal.ZERO;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "journey", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("dueDate ASC, id ASC")
    private List<Milestone> milestones = new ArrayList<>();

    public void addMilestone(Milestone m) { m.setJourney(this); milestones.add(m); }
    @PreUpdate public void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public BigDecimal getAvailableFunds() { return availableFunds; }
    public void setAvailableFunds(BigDecimal availableFunds) { this.availableFunds = availableFunds; }
    public BigDecimal getExpectedIncome() { return expectedIncome; }
    public void setExpectedIncome(BigDecimal expectedIncome) { this.expectedIncome = expectedIncome; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Milestone> getMilestones() { return milestones; }
}
