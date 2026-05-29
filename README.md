# Binance Microservice

A microservice application for interacting with Binance cryptocurrency exchange APIs and services.

## Overview

This project provides a modular microservice architecture for cryptocurrency trading and market data operations using the Binance platform. Built with a combination of Java backend services and JavaScript/Node.js frontend components.

## Tech Stack

- **Java** (50.2%) - Backend microservices
- **JavaScript** (40.6%) - Frontend and Node.js services
- **CSS** (6.9%) - UI styling
- **HTML** (2.3%) - Web templates

## Features

- Microservice architecture for scalability
- Integration with Binance APIs
- Real-time market data processing
- Trading automation capabilities
- RESTful API endpoints
- Responsive web interface

## Getting Started

### Prerequisites

- Java 8 or higher
- Node.js and npm
- Git

### Installation

1. Clone the repository:
```bash
git clone https://github.com/Chinmay5767/Binance-microservice.git
cd Binance-microservice
```

2. Set up the Java backend:
```bash
# Navigate to the Java service directory
cd backend
mvn clean install
```

3. Set up the Node.js/JavaScript services:
```bash
# Navigate to the frontend/services directory
cd frontend
npm install
```

### Configuration

1. Create a `.env` file in the root directory or service directories with your Binance API credentials:
```
BINANCE_API_KEY=your_api_key_here
BINANCE_API_SECRET=your_api_secret_here
```

2. Update service configurations as needed for your environment.

### Running the Application

#### Start Java Services
```bash
cd backend
mvn spring-boot:run
```

#### Start JavaScript Services
```bash
cd frontend
npm start
```

## Project Structure

```
Binance-microservice/
├── backend/              # Java microservices
├── frontend/             # JavaScript/Node.js services and UI
├── config/               # Configuration files
└── README.md
```

## Testing

```bash
# Run Java tests
cd backend
mvn test

# Run JavaScript tests
cd frontend
npm test
```


## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Contact

For questions or suggestions, please open an issue on the repository.

## Disclaimer

This project interacts with the Binance API. Please ensure you understand the risks of automated trading and use appropriate API keys with limited permissions. Always test thoroughly before deploying to production.
