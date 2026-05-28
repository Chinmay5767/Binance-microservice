package com.example.binanceAuth.binance_auth.service;

import java.util.Collections;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.repository.AuthUserRepo;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Service
public class CustomUserDetailsService implements UserDetailsService{
    @Autowired
	private AuthUserRepo authUserRepo;
    private static final Logger logger = LoggerFactory.getLogger(RegisterService.class);
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AuthUserEntity user = authUserRepo.findByEmailId(username)
				.orElseThrow(() -> new UsernameNotFoundException("user not found"));
		String userS = Objects.toString(user, null);
		logger.info("login service:"+userS);
		return new User(user.getUsername(), user.getPassword(), Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
	}

}
