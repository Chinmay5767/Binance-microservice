package com.example.binanceAuth.binance_auth.entity;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_user")
public class AuthUserEntity {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="user_id")
	private Long UserId;
	
	@Column(name="username")
	private String username;
	
	@Column(name="email_id")
	private String emailId;
	
	@Column(name="roles")
	private List<String> roles;
	
	public Long getUserId() {
		return UserId;
	}

	public void setUserId(Long userId) {
		UserId = userId;
	}

	public String getEmailId() {
		return emailId;
	}

	public void setEmailId(String emailId) {
		this.emailId = emailId;
	}

	public List<String> getRoles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@Column(name="password")
	private String password;
	
}
