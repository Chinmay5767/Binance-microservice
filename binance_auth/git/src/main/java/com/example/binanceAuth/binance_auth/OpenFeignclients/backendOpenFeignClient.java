package com.example.binanceAuth.binance_auth.OpenFeignclients;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.example.binanceAuth.binance_auth.entity.PortfolioDTO;
import com.example.binanceAuth.binance_auth.entity.PriceEvent;

import reactor.core.publisher.Flux;

@FeignClient("backend")
public  interface backendOpenFeignClient {

	
	@GetMapping(value = "/stream/{userId}")
	public List<PortfolioDTO> FetchUserPortfolio(@PathVariable Long userId, @RequestHeader(value = "X-User-Id", required = false) String authenticatedUserId,
            @RequestHeader(value = "X-Username", required = false) String username);
	
	 @GetMapping(value = "/stream/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PriceEvent> stream();
}
