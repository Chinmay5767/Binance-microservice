import React from 'react';
import { Card } from 'react-bootstrap';

const PriceCard = ({ title, value, percentage, type }) => {
  const getValueColor = () => {
    if (type === 'pnl') {
      return value >= 0 ? 'text-success' : 'text-danger';
    }
    return 'text-primary';
  };

  const formatValue = () => {
    if (type === 'percentage') {
      return `${percentage?.toFixed(2)}%`;
    }
    return `$${value?.toFixed(2)}`;
  };

  return (
    <Card className="text-center h-100">
      <Card.Body>
        <Card.Title className="text-muted mb-3">{title}</Card.Title>
        <h3 className={getValueColor()}>{formatValue()}</h3>
        {percentage !== undefined && type === 'pnl' && (
          <p className={`mb-0 ${percentage >= 0 ? 'text-success' : 'text-danger'}`}>
            {percentage >= 0 ? '+' : ''}{percentage.toFixed(2)}%
          </p>
        )}
      </Card.Body>
    </Card>
  );
};

export default PriceCard;