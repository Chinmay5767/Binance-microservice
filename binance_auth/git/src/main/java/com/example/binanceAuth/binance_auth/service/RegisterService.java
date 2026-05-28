package com.example.binanceAuth.binance_auth.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.binanceAuth.binance_auth.AuthUser;
import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.repository.AuthUserRepo;

@Service
public class RegisterService {

	@Autowired
	AuthUserRepo authUserRepo;
	@Autowired
	 PasswordEncoder passwordEncoder;
	private static final Logger logger = LoggerFactory.getLogger(RegisterService.class);
	public AuthUserEntity register(AuthUser request) {
		  if (authUserRepo.findByUsername(request.getUsername()).isPresent()) {
	            throw new RuntimeException("Username already exists");
	        }
		  AuthUserEntity userEntity = new AuthUserEntity();
		  String requestS = Objects.toString(userEntity, null); 
		logger.info("register user:"+requestS);
		  
		  userEntity.setUsername(request.getUsername());
		  userEntity.setEmailId(request.getEmailId());
		  userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
		  List<String> roles = new ArrayList<>();
	        roles.add("ROLE_USER");  // ← Default role
	        userEntity.setRoles(roles);
		  AuthUserEntity savedUser = new AuthUserEntity();
		  savedUser = authUserRepo.save(userEntity);
		  return savedUser;
	}
}
