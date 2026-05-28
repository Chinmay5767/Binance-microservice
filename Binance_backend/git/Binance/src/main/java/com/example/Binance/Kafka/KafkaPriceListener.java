package com.example.Binance.Kafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.Binance.PriceEvent;
import com.example.Binance.services.LivePricePublisher;

@Component
public class KafkaPriceListener {
	
	@Autowired
	LivePricePublisher livePricePublisher;

	@KafkaListener(topics = "live-prices", groupId = "binance-group1")
	public void consume(PriceEvent event) {
		System.out.println("Kafka consumed: " + event.getSymbol() + " "+ event.getPrice());
		livePricePublisher.publish(event);
	}
}
