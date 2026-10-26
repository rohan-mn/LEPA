package com.lepa.repository;
import com.lepa.model.JourneyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface JourneyEventRepository extends JpaRepository<JourneyEvent, Long> {
    List<JourneyEvent> findTop20ByOrderByOccurredAtDesc();
}
