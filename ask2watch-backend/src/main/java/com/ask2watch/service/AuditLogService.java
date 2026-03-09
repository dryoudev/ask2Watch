package com.ask2watch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuditLogService {

    public void logLoginSuccess(String email, String ip) {
        log.info("AUDIT | LOGIN_SUCCESS | email={} | ip={}", email, ip);
    }

    public void logLoginFailure(String email, String ip) {
        log.warn("AUDIT | LOGIN_FAILURE | email={} | ip={}", email, ip);
    }

    public void logRegistration(String email, String username, String ip) {
        log.info("AUDIT | REGISTRATION | email={} | username={} | ip={}", email, username, ip);
    }

    public void logDataDeletion(Long userId, String resourceType, Long resourceId) {
        log.info("AUDIT | DATA_DELETION | userId={} | resource={} | resourceId={}", userId, resourceType, resourceId);
    }

    public void logRatingChange(Long userId, Long watchedId, Integer newRating) {
        log.info("AUDIT | RATING_CHANGE | userId={} | watchedId={} | newRating={}", userId, watchedId, newRating);
    }
}
