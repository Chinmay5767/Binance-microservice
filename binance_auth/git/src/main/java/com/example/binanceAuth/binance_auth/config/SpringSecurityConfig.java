package com.example.binanceAuth.binance_auth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.binanceAuth.binance_auth.JWTFilter;

@Configuration
@EnableWebSecurity
public class SpringSecurityConfig {

	@Autowired
	CorsConfig corsConfig;
	@Autowired
	JWTFilter jwtFilter;
	@Bean
	public SecurityFilterChain  securityFilterChain (HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
		  .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
		    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
		    .authorizeHttpRequests(auth ->  auth
		   // .requestMatchers(HttpMethod.OPTIONS).permitAll()
		    .requestMatchers("/auth/*").permitAll() 
		    .anyRequest().authenticated() )
		    .formLogin(form -> form.disable())
		    .httpBasic(httpb -> httpb.disable())
		    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
		
		return http.build();
		   
	}
	
	@Bean
	public AuthenticationManager authenticationManager(
	        AuthenticationConfiguration config) throws Exception {
	    return config.getAuthenticationManager();
	}
	
	 @Bean
	    public PasswordEncoder passwordEncoder() {
	        return new BCryptPasswordEncoder();
	    }
}
