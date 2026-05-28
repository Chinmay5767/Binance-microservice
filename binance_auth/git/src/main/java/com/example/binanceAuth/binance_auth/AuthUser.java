package com.example.binanceAuth.binance_auth;

public class AuthUser {
 private String username;
  private String password;
  private String role;
  private String emailId;
  public String getEmailId() {
	return emailId;
}
  public void setEmailId(String emailId) {
	this.emailId = emailId;
  }
  public String getRole() {
	return role;
}
  public void setRole(String role) {
	this.role = role;
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
}
