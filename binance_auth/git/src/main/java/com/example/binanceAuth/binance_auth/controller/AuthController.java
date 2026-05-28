package com.example.binanceAuth.binance_auth.controller;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.binanceAuth.binance_auth.AuthUser;
import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.repository.AuthUserRepo;
import com.example.binanceAuth.binance_auth.service.JWTService;
import com.example.binanceAuth.binance_auth.service.RegisterService;

@RestController
@RequestMapping("/auth")
public class AuthController {
	private static final Logger logger =
            LoggerFactory.getLogger(AuthController.class);
	
     @Autowired
	 private  AuthenticationManager authenticationManager;
     @Autowired
     private AuthUserRepo authUserRepo;
     @Autowired
     private RegisterService registerService;
     @Autowired
   	 private  JWTService jwtService;
	@PostMapping("/login")
	public ResponseEntity<?> login (@RequestBody AuthUser request) throws AuthenticationException{
		logger.info("user trying to login is: "+request.getEmailId()+" 0"+request.getUsername()+" "+request.getPassword());
		try {
			
			Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(
			                request.getEmailId(),
			                request.getPassword()
			        )
					);
			   AuthUserEntity user = authUserRepo.findByEmailId(request.getEmailId())
			            .orElseThrow(() -> new RuntimeException("User not found"));
			String token = jwtService.generateJWTtoken(request.getEmailId(), user.getUserId());
			logger.info(token);
			return ResponseEntity.ok().body(Map.of(
					"accessToken", token,      
		            "tokenType", "Bearer",     
		            "userId", user.getUserId(),     
		            "username", request.getEmailId(),
		            "msg", "Login successful"

					));
		} catch (AuthenticationException e) {
		  logger.error("Exception is: ",e);
			return ResponseEntity.status(500).body(Map.of("msg", "something went wrong"));
		}
		
	}
	
	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody AuthUser request){
		logger.info("user trying to register is: "+request.getEmailId()+" 0"+request.getUsername()+" "+request.getPassword());
		try {
		AuthUserEntity registeredUser = registerService.register(request);
		return ResponseEntity.ok().body(Map.of("msg","Registered successfully"));
		}
		catch(Exception e) {
			logger.error("Exception is: "+e.toString());
			return ResponseEntity.status(500).body(Map.of("msg", "something went wrong"));
		}
		
	}
}
