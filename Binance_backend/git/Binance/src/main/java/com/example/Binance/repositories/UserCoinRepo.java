package com.example.Binance.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.Binance.PriceEvent;
import com.example.Binance.Entity.UserCoin;

import reactor.core.publisher.Flux;

@Repository
public interface UserCoinRepo extends JpaRepository <UserCoin, Long> {
	
	

	List<UserCoin> findByUserId(Long userId);

}
