package com.example.binanceAuth.binance_auth.service;

import com.example.binanceAuth.binance_auth.model.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SESSION_KEY = "session:";
    private static final String USER_SESSIONS_KEY = "user:sessions:";
    private static final String ACTIVE_SESSIONS_KEY = "active:sessions:";

    /**
     * ✅ Store user session in Redis (Shared across all microservices)
     */
    public void storeSession(UserSession session) {
        try {
            String key = SESSION_KEY + session.getSessionId();
            
            // Calculate expiry time
            long ttlSeconds = (session.getExpiresAt() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds <= 0) {
                ttlSeconds = 86400; // Default 24 hours
            }
            
            redisTemplate.opsForValue().set(
                key, 
                session, 
                ttlSeconds, 
                TimeUnit.SECONDS
            );
            
            // Track user's sessions
            String userKey = USER_SESSIONS_KEY + session.getUserId();
            redisTemplate.opsForSet().add(userKey, session.getSessionId());
            redisTemplate.expire(userKey, ttlSeconds + 3600, TimeUnit.SECONDS);
            
            // Track active sessions globally
            String activeKey = ACTIVE_SESSIONS_KEY + session.getSessionId();
            redisTemplate.opsForValue().set(
                activeKey, 
                "active", 
                ttlSeconds, 
                TimeUnit.SECONDS
            );
            
            log.debug("Session stored in Redis: {}", session.getSessionId());
            
        } catch (Exception e) {
            log.error("Failed to store session in Redis: {}", e.getMessage());
        }
    }

    /**
     * ✅ Get session from Redis (Any microservice can access)
     */
    public UserSession getSession(String sessionId) {
        try {
            String key = SESSION_KEY + sessionId;
            return (UserSession) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Failed to get session from Redis: {}", e.getMessage());
            return null;
        }
    }

    /**
     * ✅ Get all active sessions for a user
     */
    public List<UserSession> getUserSessions(Long userId) {
        try {
            String userKey = USER_SESSIONS_KEY + userId;
            Set<Object> sessionIds = redisTemplate.opsForSet().members(userKey);
            
            List<UserSession> sessions = new ArrayList<>();
            if (sessionIds != null) {
                for (Object id : sessionIds) {
                    UserSession session = getSession(id.toString());
                    if (session != null && session.isActive()) {
                        sessions.add(session);
                    }
                }
            }
            
            // Sort by last activity (most recent first)
            sessions.sort((s1, s2) -> 
                Long.compare(s2.getLastActivityAt(), s1.getLastActivityAt())
            );
            
            return sessions;
            
        } catch (Exception e) {
            log.error("Failed to get user sessions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * ✅ Update session activity
     */
    public void updateSessionActivity(String sessionId) {
        try {
            UserSession session = getSession(sessionId);
            if (session != null) {
                session.setLastActivityAt(System.currentTimeMillis());
                storeSession(session);
            }
        } catch (Exception e) {
            log.error("Failed to update session activity: {}", e.getMessage());
        }
    }

    /**
     * ✅ Invalidate session (logout)
     */
    public void invalidateSession(String sessionId) {
        try {
            UserSession session = getSession(sessionId);
            
            if (session != null) {
                // Remove from user's sessions
                String userKey = USER_SESSIONS_KEY + session.getUserId();
                redisTemplate.opsForSet().remove(userKey, sessionId);
                
                // Mark as inactive
                session.setActive(false);
                String key = SESSION_KEY + sessionId;
                redisTemplate.opsForValue().set(
                    key, 
                    session, 
                    3600, 
                    TimeUnit.SECONDS
                );
                
                // Remove from active sessions
                String activeKey = ACTIVE_SESSIONS_KEY + sessionId;
                redisTemplate.delete(activeKey);
            } else {
                // Just delete the session
                String key = SESSION_KEY + sessionId;
                redisTemplate.delete(key);
            }
            
            log.info("Session invalidated: {}", sessionId);
            
        } catch (Exception e) {
            log.error("Failed to invalidate session: {}", e.getMessage());
        }
    }

    /**
     * ✅ Invalidate ALL sessions for a user (logout all devices)
     */
    public void invalidateAllSessions(Long userId) {
        try {
            String userKey = USER_SESSIONS_KEY + userId;
            Set<Object> sessionIds = redisTemplate.opsForSet().members(userKey);
            
            if (sessionIds != null) {
                for (Object id : sessionIds) {
                    String sessionId = id.toString();
                    invalidateSession(sessionId);
                }
                redisTemplate.delete(userKey);
            }
            
            log.info("All sessions invalidated for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to invalidate all sessions: {}", e.getMessage());
        }
    }

    /**
     * ✅ Get active session count globally
     */
    public long getActiveSessionCount() {
        try {
            String pattern = ACTIVE_SESSIONS_KEY + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("Failed to get active session count: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * ✅ Check if session is active
     */
    public boolean isSessionActive(String sessionId) {
        try {
            String activeKey = ACTIVE_SESSIONS_KEY + sessionId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(activeKey));
        } catch (Exception e) {
            log.error("Failed to check session activity: {}", e.getMessage());
            return false;
        }
    }
}