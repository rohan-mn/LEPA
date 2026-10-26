package com.lepa.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "milestones")
public class Milestone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "journey_id") @JsonIgnore
    private Journey journey;
    private String name;
    private String category;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String status;
    private String source;
    private String paymentRail;
    private String paymentId;

    public Long getId() { return id; }
    public Journey getJourney() { return journey; }
    public void setJourney(Journey journey) { this.journey = journey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPaymentRail() { return paymentRail; }
    public void setPaymentRail(String paymentRail) { this.paymentRail = paymentRail; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
}
