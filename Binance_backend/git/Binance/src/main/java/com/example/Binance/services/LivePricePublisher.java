package com.example.Binance.services;

import org.apache.kafka.streams.processor.internals.InternalTopologyBuilder.Sink;
import org.springframework.stereotype.Service;

import com.example.Binance.PriceEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

@Service
public class LivePricePublisher {

	private final Many<PriceEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
	
	public void publish(PriceEvent priceEvent) {
		sink.tryEmitNext(priceEvent);
	}
	
	public Flux<PriceEvent> flux() {
		return sink.asFlux();
	}
}
