import React, { useState, useEffect } from 'react';
import { Card, Table, Badge } from 'react-bootstrap';
import { fetchLivePrices } from '../services/api';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

const PriceStream = () => {
  const [prices, setPrices] = useState({});
  const [priceHistory, setPriceHistory] = useState({});
  const [selectedSymbol, setSelectedSymbol] = useState('BTCUSDT');

  useEffect(() => {
    const es = fetchLivePrices((data) => {
      setPrices(prev => ({
        ...prev,
        [data.symbol]: parseFloat(data.price)
      }));

      // Update price history
      setPriceHistory(prev => {
        const symbolHistory = prev[data.symbol] || [];
        const newHistory = [
          ...symbolHistory,
          {
            time: new Date(data.timestamp).toLocaleTimeString(),
            price: parseFloat(data.price)
          }
        ].slice(-20); // Keep last 20 data points
        return {
          ...prev,
          [data.symbol]: newHistory
        };
      });
    });

    return () => es.close();
  }, []);

  const getPriceChange = (symbol) => {
    const history = priceHistory[symbol] || [];
    if (history.length < 2) return 0;
    const first = history[0].price;
    const last = history[history.length - 1].price;
    return ((last - first) / first * 100).toFixed(2);
  };

  const sortedSymbols = Object.keys(prices).sort();

  return (
    <div>
      <div className="row">
        <div className="col-md-8">
          <Card className="mb-4">
            <Card.Header>
              <h5 className="mb-0">Price Chart - {selectedSymbol}</h5>
            </Card.Header>
            <Card.Body>
              <div style={{ height: '400px' }}>
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart
                    data={priceHistory[selectedSymbol] || []}
                    margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="time" />
                    <YAxis domain={['auto', 'auto']} />
                    <Tooltip />
                    <Line 
                      type="monotone" 
                      dataKey="price" 
                      stroke="#007bff" 
                      dot={false}
                      strokeWidth={2}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </Card.Body>
          </Card>
        </div>
        
        <div className="col-md-4">
          <Card className="mb-4">
            <Card.Header>
              <h5 className="mb-0">Symbol Selector</h5>
            </Card.Header>
            <Card.Body style={{ maxHeight: '400px', overflowY: 'auto' }}>
              <div className="d-flex flex-wrap gap-2">
                {sortedSymbols.map(symbol => {
                  const change = getPriceChange(symbol);
                  return (
                    <Badge
                      key={symbol}
                      bg={selectedSymbol === symbol ? 'primary' : 'secondary'}
                      style={{ cursor: 'pointer', padding: '8px 12px' }}
                      onClick={() => setSelectedSymbol(symbol)}
                    >
                      {symbol.replace('USDT', '')}
                      <small className="ms-2">
                        ${prices[symbol].toFixed(2)}
                      </small>
                      {change !== 0 && (
                        <small className={`ms-2 ${change > 0 ? 'text-success' : 'text-danger'}`}>
                          ({change > 0 ? '+' : ''}{change}%)
                        </small>
                      )}
                    </Badge>
                  );
                })}
              </div>
            </Card.Body>
          </Card>
        </div>
      </div>

      <Card>
        <Card.Header>
          <h5 className="mb-0">Live Prices</h5>
        </Card.Header>
        <Card.Body>
          <Table responsive hover>
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Price (USDT)</th>
                <th>24h Change</th>
                <th>Last Update</th>
              </tr>
            </thead>
            <tbody>
              {sortedSymbols.map(symbol => {
                const change = getPriceChange(symbol);
                const history = priceHistory[symbol] || [];
                const lastUpdate = history.length > 0 
                  ? history[history.length - 1].time 
                  : 'N/A';

                return (
                  <tr key={symbol}>
                    <td>
                      <strong>{symbol.replace('USDT', '')}</strong>
                      <Badge bg="info" className="ms-2">USDT</Badge>
                    </td>
                    <td className={change > 0 ? 'price-up' : change < 0 ? 'price-down' : ''}>
                      ${prices[symbol].toFixed(2)}
                    </td>
                    <td>
                      <Badge bg={change > 0 ? 'success' : change < 0 ? 'danger' : 'secondary'}>
                        {change > 0 ? '+' : ''}{change}%
                      </Badge>
                    </td>
                    <td>{lastUpdate}</td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
        </Card.Body>
      </Card>
    </div>
  );
};

export default PriceStream;