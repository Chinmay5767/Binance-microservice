package com.example.Binance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class PriceCache {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String getPrice(String symbol) {
        return cache.get(symbol);
    }

    public void updatePrice(String symbol, String price) {
        cache.put(symbol, price);
    }
}