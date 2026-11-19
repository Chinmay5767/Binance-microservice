package com.example.binanceAuth.binance_auth.repository;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthUserRepo extends JpaRepository<AuthUserEntity, Long> {
    
    Optional<AuthUserEntity> findByEmailId(String email);
    
    Optional<AuthUserEntity> findByUsername(String username);
    
    Optional<AuthUserEntity> findByKeycloakId(String keycloakId);
    
    boolean existsByEmailId(String email);
    
    boolean existsByUsername(String username);
}