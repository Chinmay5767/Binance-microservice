package com.example.Binance.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.Binance.PortfolioDTO;
import com.example.Binance.PriceEvent;
import com.example.Binance.Entity.UserCoin;
import com.example.Binance.repositories.UserCoinRepo;
@Service
public class PortFolioService {
@Autowired
UserCoinRepo userCoinRepo;
@Autowired
WebsocketService websocketService;
	public List<PortfolioDTO> fetchPortfolioPrices(Long userId) {
		List<UserCoin> coins = userCoinRepo.findByUserId(userId);
		coins.forEach(c->
		{  System.out.println("coins info:"+c.getSymbol()+ c.getQuantity());
			websocketService.createStream(c.getSymbol());
		  }
		);
		return coins.stream().
				map(c-> new PortfolioDTO(
						c.getSymbol(), c.getQuantity(), c.getBuyPrice())
						).toList();
	   
	}

}
