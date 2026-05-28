import React, { useState, useEffect, useRef } from 'react';
import { Table, Card, Spinner, Alert, Badge } from 'react-bootstrap';
import { fetchPortfolio, fetchLivePrices } from '../services/api';
import PriceCard from './PriceCard';

const Portfolio = ({ userId }) => {
  const [portfolio, setPortfolio] = useState([]);
  const [livePrices, setLivePrices] = useState({});
  const [loading, setLoading] = useState(true);
  const [streamConnected, setStreamConnected] = useState(false);
  const [error, setError] = useState(null);
  const eventSourceRef = useRef(null);

  useEffect(() => {
    loadPortfolio();
    
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, [userId]);

  const loadPortfolio = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await fetchPortfolio(userId);
      setPortfolio(data);
      
      // Only setup price stream if we have portfolio items
      if (data && data.length > 0) {
        setupPriceStream();
      } else {
        setLoading(false);
      }
    } catch (err) {
      setError('Failed to load portfolio. Please try again.');
      console.error('Error loading portfolio:', err);
      setLoading(false);
    }
  };

  const setupPriceStream = () => {
    // Close existing connection if any
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setStreamConnected(false);
    
    const es = fetchLivePrices(
      // onMessage callback
      (data) => {
        setLivePrices(prev => ({
          ...prev,
          [data.symbol]: parseFloat(data.price)
        }));
        setStreamConnected(true);
      },
      // onError callback
      (error) => {
        console.error('Stream error:', error);
        setStreamConnected(false);
      }
    );
    
    eventSourceRef.current = es;
    
    // Set a timeout to check if stream is connected
    setTimeout(() => {
      if (!streamConnected) {
        console.log('Stream connection timeout - retrying...');
        setupPriceStream();
      }
    }, 5000);
  };

  // Retry stream connection if disconnected
  useEffect(() => {
    if (!streamConnected && portfolio.length > 0 && !loading) {
      const timer = setTimeout(() => {
        setupPriceStream();
      }, 3000);
      
      return () => clearTimeout(timer);
    }
  }, [streamConnected, portfolio, loading]);

  const calculatePnL = (buyPrice, currentPrice) => {
    if (!currentPrice) return { value: 0, percentage: 0 };
    const buy = parseFloat(buyPrice);
    const current = parseFloat(currentPrice);
    const diff = current - buy;
    const percentage = (diff / buy) * 100;
    return { value: diff, percentage };
  };

  const calculateTotalValue = () => {
    return portfolio.reduce((total, item) => {
      const currentPrice = livePrices[item.symbol + 'USDT'] || 0;
      return total + (item.quantity * currentPrice);
    }, 0);
  };

  const calculateTotalInvestment = () => {
    return portfolio.reduce((total, item) => {
      return total + (item.quantity * item.buyPrice);
    }, 0);
  };

  if (loading) {
    return (
      <div className="loading-spinner">
        <Spinner animation="border" variant="primary" />
        <span className="ms-3">Loading portfolio...</span>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="danger">
        {error}
        <button 
          className="btn btn-primary btn-sm ms-3"
          onClick={() => loadPortfolio()}
        >
          Retry
        </button>
      </Alert>
    );
  }

  if (portfolio.length === 0) {
    return (
      <Card>
        <Card.Body className="text-center py-5">
          <h4>No Portfolio Items Found</h4>
          <p className="text-muted">
            You don't have any cryptocurrency in your portfolio yet.
          </p>
        </Card.Body>
      </Card>
    );
  }

  const totalValue = calculateTotalValue();
  const totalInvestment = calculateTotalInvestment();
  const totalPnL = totalValue - totalInvestment;
  const totalPnLPercentage = totalInvestment > 0 ? (totalPnL / totalInvestment) * 100 : 0;

  return (
    <div>
      {/* Connection Status Banner */}
      {!streamConnected && (
        <Alert variant="warning" className="mb-3">
          <Spinner
            as="span"
            animation="border"
            size="sm"
            role="status"
            aria-hidden="true"
            className="me-2"
          />
          Connecting to live price stream...
        </Alert>
      )}

      <div className="row mb-4">
        <div className="col-md-4">
          <PriceCard
            title="Total Investment"
            value={totalInvestment}
            type="investment"
          />
        </div>
        <div className="col-md-4">
          <PriceCard
            title="Current Value"
            value={totalValue}
            type="value"
          />
        </div>
        <div className="col-md-4">
          <PriceCard
            title="Total P&L"
            value={totalPnL}
            percentage={totalPnLPercentage}
            type="pnl"
          />
        </div>
      </div>

      <Card>
        <Card.Header className="d-flex justify-content-between align-items-center">
          <h5 className="mb-0">Portfolio Holdings</h5>
          <div>
            <Badge bg={streamConnected ? "success" : "warning"}>
              {streamConnected ? "Live" : "Connecting..."}
            </Badge>
          </div>
        </Card.Header>
        <Card.Body>
          <Table responsive hover>
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Quantity</th>
                <th>Buy Price</th>
                <th>Current Price</th>
                <th>Current Value</th>
                <th>P&L</th>
                <th>P&L %</th>
              </tr>
            </thead>
            <tbody>
              {portfolio.map((item, index) => {
                const symbol = item.symbol + 'USDT';
                const currentPrice = livePrices[symbol];
                const hasLivePrice = currentPrice !== undefined;
                const currentValue = hasLivePrice ? item.quantity * currentPrice : 0;
                const pnl = calculatePnL(item.buyPrice, currentPrice);
                const isPositive = pnl.value >= 0;

                return (
                  <tr key={index}>
                    <td>
                      <strong>{item.symbol}</strong>
                      <Badge bg="info" className="ms-2">USDT</Badge>
                    </td>
                    <td>{item.quantity.toFixed(4)}</td>
                    <td>${item.buyPrice.toFixed(2)}</td>
                    <td className={hasLivePrice ? (isPositive ? 'price-up' : 'price-down') : ''}>
                      {hasLivePrice ? `$${currentPrice.toFixed(2)}` : 
                        <Spinner animation="border" size="sm" />}
                    </td>
                    <td>
                      {hasLivePrice ? `$${currentValue.toFixed(2)}` : 
                        <Spinner animation="border" size="sm" />}
                    </td>
                    <td className={hasLivePrice ? (isPositive ? 'price-up' : 'price-down') : ''}>
                      {hasLivePrice ? `$${(item.quantity * pnl.value).toFixed(2)}` : '-'}
                    </td>
                    <td>
                      {hasLivePrice ? (
                        <Badge bg={isPositive ? 'success' : 'danger'}>
                          {isPositive ? '+' : ''}{pnl.percentage.toFixed(2)}%
                        </Badge>
                      ) : (
                        <Badge bg="secondary">Waiting...</Badge>
                      )}
                    </td>
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

export default Portfolio;