package com.example.binanceAuth.binance_auth.service;

import java.security.Key;
import java.security.NoSuchAlgorithmException;

import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import java.util.function.Function;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JWTService {
	private String secretKey = "";
	 private ThreadLocal<String> currentToken = new ThreadLocal<>();
	public JWTService() {
		
		try {
			KeyGenerator keygen = KeyGenerator.getInstance("HmacSHA256");
			SecretKey sk = keygen.generateKey();
			secretKey = Base64.getEncoder().encodeToString(sk.getEncoded());
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	public Key getKey() {
			byte[]  keyBytes = Decoders.BASE64.decode(secretKey);
			return Keys.hmacShaKeyFor(keyBytes);
	}

	public String generateJWTtoken(String username, long userId) {
		Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("userId", userId);      // Add userId to claims
        claims.put("role", "USER");
		return Jwts.builder()
				.claims(claims)
				.subject(String.valueOf(userId))
				.issuedAt(new Date(System.currentTimeMillis()))
				.expiration(new Date(System.currentTimeMillis() + 60*60*30))
				.signWith(getKey())
				.compact();	
	}
	
	 public String extractUsername(String token) {
	        return extractClaim(token, Claims::getSubject);
	    }
	    
	    public Date extractExpiration(String token) {
	        return extractClaim(token, Claims::getExpiration);
	    }
	    
	    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
	        final Claims claims = extractAllClaims(token);
	        return claimsResolver.apply(claims);
	    }
	    public Long extractUserId(String token) {
	        Claims claims = extractAllClaims(token);
	        return claims.get("userId", Long.class);
	    }
	    private Claims extractAllClaims(String token) {
	        return Jwts.parser()
	                .setSigningKey(getKey())
	                .build()
	                .parseClaimsJws(token)
	                .getBody();
	    }
	    
	    private Boolean isTokenExpired(String token) {
	        return extractExpiration(token).before(new Date(0));
	    }
	    
	    // Store token before validation
	    public void setCurrentToken(String token) {
	        currentToken.set(token);
	    }
	    
	    public void clearCurrentToken() {
	        currentToken.remove();
	    }
	    
	    // Modified to work with stored token
	    public boolean validateToken(UserDetails userdetails) {
	        String token = currentToken.get();
	        if (token == null) {
	            return false;
	        }
	        final String username = extractUsername(token);
	        return (username.equals(userdetails.getUsername()) && !isTokenExpired(token));
	    }
}
