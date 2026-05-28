package com.example.Binance;

public class PortfolioDTO {
private String symbol;
private Double quantity;
private Double buyPrice;

public PortfolioDTO(String symbol, Double quantity, Double buyPrice) {
	super();
	this.symbol = symbol;
	this.quantity = quantity;
	this.buyPrice = buyPrice;
	
}
public String getSymbol() {
	return symbol;
}
public void setSymbol(String symbol) {
	this.symbol = symbol;
}
public Double getQuantity() {
	return quantity;
}
public void setQuantity(Double quantity) {
	this.quantity = quantity;
}
public Double getBuyPrice() {
	return buyPrice;
}
public void setBuyPrice(Double buyPrice) {
	this.buyPrice = buyPrice;
}
}
