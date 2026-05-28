package com.example.Binance;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PriceEvent {
	@JsonProperty("symbol")
    private String symbol;
	@JsonProperty("price")
    private String price;
	@JsonProperty("timestamp")
    private long timestamp;
	
	 public PriceEvent() {
	    }

    public PriceEvent(String symbol, String price, long timestamp) {
        this.symbol = symbol;
        this.price = price;
        this.timestamp = timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getPrice() {
        return price;
    }

    public long getTimestamp() {
        return timestamp;
    }
    
    public void setSymbol(String symbol) {   
        this.symbol = symbol;
    }


    public void setPrice(String price) {    
        this.price = price;
    }


    public void setTimestamp(long timestamp) { 
        this.timestamp = timestamp;
    }
}
