package com.binance.APIGatewayService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        
        // Log request
        log.info("Incoming request: {} {} from {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(),
                exchange.getRequest().getRemoteAddress());
        
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    // Log response
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Response status: {} - Duration: {}ms",
                            exchange.getResponse().getStatusCode(),
                            duration);
                }));
    }

    @Override
    public int getOrder() {
        return -200; // Run before JWT filter
    }
}
