package com.example.binanceAuth.binance_auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;



@Repository
public interface AuthUserRepo extends JpaRepository <AuthUserEntity, Long> {

	 Optional<AuthUserEntity> findByUsername(String username);
	 Optional<AuthUserEntity> findByEmailId(String username);
}
