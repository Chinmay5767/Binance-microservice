import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import './App.css';
import Navbar from './components/Navbar';
import Portfolio from './components/Portfolio';
import PriceStream from './components/PriceStream';
import Login from './components/Login';
import Register from './components/Register';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [userInfo, setUserInfo] = useState(null);
  const [userId, setUserId] = useState(1);

  const handleLogin = (loginData) => {
    setIsAuthenticated(true);
    setUserInfo(loginData);
    setUserId(loginData.userId);
  };

  const handleRegister = (registrationData) => {
    setUserInfo(registrationData);
    // User will be redirected to login after registration
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setUserInfo(null);
  };

  return (
    <Router>
      <div className="App">
        {isAuthenticated && <Navbar userInfo={userInfo} onLogout={handleLogout} />}
        
        {!isAuthenticated ? (
          // Authentication Routes
          <Routes>
            <Route path="/" element={<Navigate to="/login" />} />
            <Route path="/login" element={<Login onLogin={handleLogin} />} />
            <Route path="/register" element={<Register onRegister={handleRegister} />} />
            <Route path="*" element={<Navigate to="/login" />} />
          </Routes>
        ) : (
          // Protected Routes
          <div className="container mt-4">
            <div className="row mb-4">
              <div className="col">
                <h1 className="text-center">Binance Portfolio Tracker</h1>
                <p className="text-center text-muted">
                  Track your cryptocurrency portfolio with real-time prices
                </p>
              </div>
            </div>
            
            <Routes>
              <Route path="/" element={<Navigate to="/portfolio" />} />
              <Route path="/portfolio" element={<Portfolio userId={userId} />} />
              <Route path="/stream" element={<PriceStream />} />
              <Route path="*" element={<Navigate to="/portfolio" />} />
            </Routes>
          </div>
        )}
      </div>
    </Router>
  );
}

export default App;