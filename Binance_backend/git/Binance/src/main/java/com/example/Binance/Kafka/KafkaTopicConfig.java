package com.example.Binance.Kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	
	@Bean
	public NewTopic LivePriceTopic() {
		return TopicBuilder.name("live-prices").partitions(6).replicas(1).build();
	}
	
	@Bean
	public NewTopic UserPortFolioTopic() {
		return TopicBuilder.name("user-portfolio").partitions(6).replicas(1).build();
	}
	@Bean
	public NewTopic UserPercentageChange() {
		return TopicBuilder.name("user-percentage-change").partitions(6).replicas(1).build();
	}
}
