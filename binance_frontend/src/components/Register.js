import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { registerUser } from '../services/api';
import './Register.css';

function Register({ onRegister }) {
  const [formData, setFormData] = useState({
    username: '',
    emailId: '',
    password: '',
    confirmPassword: '',
  });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const navigate = useNavigate();

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData({
      ...formData,
      [name]: value,
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    // Basic validation
    if (!formData.username || !formData.emailId || !formData.password || !formData.confirmPassword) {
      setError('Please fill in all fields');
      return;
    }

    if (!formData.emailId.includes('@')) {
      setError('Please enter a valid emailId');
      return;
    }

    if (formData.password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }

    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    try {
      await registerUser(formData.username, formData.emailId, formData.password);
      setSuccess('Registration successful! Redirecting to login...');
      setTimeout(() => {
        navigate('/login');
      }, 1500);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="register-container">
      <div className="register-box">
        <h1 className="register-title">Binance Portfolio Tracker</h1>
        <h2 className="register-subtitle">Create Account</h2>

        {error && <div className="alert alert-danger">{error}</div>}
        {success && <div className="alert alert-success">{success}</div>}

        <form onSubmit={handleSubmit}>
          <div className="mb-3">
            <label htmlFor="username" className="form-label">
              Full Name
            </label>
            <input
              type="text"
              className="form-control"
              id="username"
              name="username"
              placeholder="Enter your full name"
              value={formData.username}
              onChange={handleChange}
            />
          </div>

          <div className="mb-3">
            <label htmlFor="emailId" className="form-label">
              email Address
            </label>
            <input
              type="emailId"
              className="form-control"
              id="emailId"
              name="emailId"
              placeholder="Enter your email"
              value={formData.emailId}
              onChange={handleChange}
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
              name="password"
              placeholder="Enter your password"
              value={formData.password}
              onChange={handleChange}
            />
            <small className="text-muted d-block mt-1">
              Minimum 6 characters
            </small>
          </div>

          <div className="mb-3">
            <label htmlFor="confirmPassword" className="form-label">
              Confirm Password
            </label>
            <input
              type="password"
              className="form-control"
              id="confirmPassword"
              name="confirmPassword"
              placeholder="Confirm your password"
              value={formData.confirmPassword}
              onChange={handleChange}
            />
          </div>

          <button type="submit" className="btn btn-primary w-100 mb-3">
            Register
          </button>
        </form>

        <hr />

        <p className="text-center text-muted mb-0">
          Already have an account?{' '}
          <button
            className="btn btn-link p-0"
            onClick={() => navigate('/login')}
          >
            Login here
          </button>
        </p>
      </div>
    </div>
  );
}

export default Register;
