package com.example.Binance.controller;

import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.example.Binance.PortfolioDTO;
import com.example.Binance.PriceEvent;
import com.example.Binance.services.LivePricePublisher;
import com.example.Binance.services.PortFolioService;

import reactor.core.publisher.Flux;
@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class FetchPriceController {
    @Autowired
    PortFolioService portFolioService;
    @Autowired
    LivePricePublisher livePricePublisher;
    
    @GetMapping(value = "/stream/{userId}")
    public List<PortfolioDTO> FetchUserPortfolio(@PathVariable Long userId, @RequestHeader(value = "X-User-Id", required = false) String authenticatedUserId,
            @RequestHeader(value = "X-Username", required = false) String username) {
    	  if (!authenticatedUserId.equals(String.valueOf(userId))) {
              throw new RuntimeException("Unauthorized: Cannot access another user's portfolio");
          }
    	System.out.println("Created for userId: " + userId);
    	return portFolioService.fetchPortfolioPrices(userId);
		 
    }    
    
    @GetMapping(value = "/stream/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PriceEvent> stream() {
    	return livePricePublisher.flux();
		 
    } 
}

