package com.example.Binance.services;

import com.example.Binance.PriceEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

@Service
public class WebsocketService {

    private static final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    KafkaTemplate<String, PriceEvent> kafkaTemplate;
	ConcurrentHashMap <String, Flux<PriceEvent>> stream = new ConcurrentHashMap<>();
	
	public void createStream(String symbol) {
		stream.computeIfAbsent(symbol, s -> {
			Flux<PriceEvent> flux =  createWebsocketStreamForUser(symbol)
					.doOnSubscribe(a -> 
				System.out.println("Created stream for symbol: " + symbol)
			);
			flux.subscribe();
			return flux;
		
		});
	}

    public Flux<PriceEvent> createWebsocketStreamForUser(String symbol) {

        String url = "wss://stream.binance.com:9443/ws/"
                   + symbol.toLowerCase() + "usdt@trade";

        return HttpClient.create()
                .websocket()
                .uri(url)
                .handle((inbound, outbound) ->
                        inbound.receive()
                               .asString()
                               .map(this::parseMessage)
                               .doOnNext(event -> {
                            	   System.out.println("Received from Binance: " + event.getSymbol()+" " + event.getPrice());
                               kafkaTemplate.send("live-prices", event.getSymbol(), event);
                               }
                            		   )
                ).share()
                .retry();
    }

    private PriceEvent parseMessage(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            return new PriceEvent(
                    node.get("s").asText(),   // Symbol
                    node.get("p").asText(),   // Price
                    node.get("T").asLong()    // Trade time
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse message", e);
        }
    }
}
