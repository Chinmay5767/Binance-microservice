package com.example.binanceAuth.binance_auth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.binanceAuth.binance_auth.service.CustomUserDetailsService;
import com.example.binanceAuth.binance_auth.service.JWTService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
@Component
public class JWTFilter extends OncePerRequestFilter{
	@Autowired
	JWTService jwtservice;
	@Autowired
	CustomUserDetailsService customUserDetailsService;
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// we will get token from client or frontend like this
		// Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhc2RAZ21haWwuY29tIiwiaWF0IjoxNzc1Mzg1NDQzLCJleHAiOjE3NzUzODU1NTF9.SbbedG0jg7O7gwC1qpjyxgZZV34suPxI092zyohSsRE
		
		
		String authHeader = request.getHeader("Authorization");
		String username = null;
		String token = null;
		if(authHeader!=null && authHeader.startsWith("Bearer")) {
			token = authHeader.substring(7);
			username = jwtservice.extractUsername(token);
		}
		
		if(username!=null && SecurityContextHolder.getContext().getAuthentication()==null) {
			
			UserDetails userdetails = customUserDetailsService.loadUserByUsername(username);
			if(jwtservice.validateToken(userdetails)==true) {
			UsernamePasswordAuthenticationToken authToken =  new UsernamePasswordAuthenticationToken(userdetails, null,userdetails.getAuthorities());
			authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authToken);
			
			}
		}
				
		filterChain.doFilter(request, response);
	}

}
