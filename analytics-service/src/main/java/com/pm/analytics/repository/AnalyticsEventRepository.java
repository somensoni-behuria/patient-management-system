package com.pm.analytics.repository;

import com.pm.analytics.model.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    long countByEventType(String eventType);
}
