import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { loginUser } from '../services/api';
import './Login.css';

function Login({ onLogin }) {
  const [emailId, setemailId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    // Basic validation
    if (!emailId || !password) {
      setError('Please fill in all fields');
      return;
    }

    if (!emailId.includes('@')) {
      setError('Please enter a valid email');
      return;
    }

    try {
      const userData = await loginUser(emailId, password);
      onLogin(userData);
      navigate('/portfolio');
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="login-container">
      <div className="login-box">
        <h1 className="login-title">Binance Portfolio Tracker</h1>
        <h2 className="login-subtitle">Login</h2>

        {error && <div className="alert alert-danger">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="mb-3">
            <label htmlFor="emailId" className="form-label">
              email Address
            </label>
            <input
              type="emailId"
              className="form-control"
              id="emailId"
              placeholder="Enter your email"
              value={emailId}
              onChange={(e) => setemailId(e.target.value)}
            />
          </div>

          <div className="mb-3">
            <label htmlFor="password" className="form-label">
              Password
            </label>
            <input
              type="password"
              className="form-control"
              id="password"
              placeholder="Enter your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <button type="submit" className="btn btn-primary w-100 mb-3">
            Login
          </button>
        </form>

        <hr />

        <p className="text-center text-muted mb-0">
          Don't have an account?{' '}
          <button
            className="btn btn-link p-0"
            onClick={() => navigate('/register')}
          >
            Register here
          </button>
        </p>
      </div>
    </div>
  );
}

export default Login;
