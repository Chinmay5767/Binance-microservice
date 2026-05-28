import axios from 'axios';

const API_GATEWAY_URL = 'http://localhost:8080/api';

// Login user
export const loginUser = async (emailId, password) => {
  try {
    console.log('Logging in user:', emailId);
    const response = await axios.post(`${API_GATEWAY_URL}/auth/login`, {
      emailId,
      password,
    });
    console.log('Login successful:', response.data);
    return response.data;
  } catch (error) {
    console.error('Error logging in:', error);
    if (error.response) {
      throw new Error(`Login failed: ${error.response.data.message || error.response.statusText}`);
    } else if (error.request) {
      throw new Error('No response from server. Please check if backend is running.');
    } else {
      throw new Error(`Error: ${error.message}`);
    }
  }
};

// Register user
export const registerUser = async (username, emailId, password) => {
  try {
    console.log('Registering user:', emailId);
    const response = await axios.post(`${API_GATEWAY_URL}/auth/register`, {
      username,
      emailId,
      password,
    });
    console.log('Registration successful:', response.data);
    return response.data;
  } catch (error) {
    console.error('Error registering:', error);
    if (error.response) {
      throw new Error(`Registration failed: ${error.response.data.message || error.response.statusText}`);
    } else if (error.request) {
      throw new Error('No response from server. Please check if backend is running.');
    } else {
      throw new Error(`Error: ${error.message}`);
    }
  }
};

// Fetch user portfolio - using named export
export const fetchPortfolio = async (userId) => {
  try {
    console.log(`Fetching portfolio for user: ${userId}`);
    const response = await axios.get(`${API_BASE_URL}/stream/20`);
    console.log('Portfolio data received:', response.data);
    return response.data;
  } catch (error) {
    console.error('Error fetching portfolio:', error);
    if (error.response) {
      throw new Error(`Server error: ${error.response.status}`);
    } else if (error.request) {
      throw new Error('No response from server. Please check if backend is running.');
    } else {
      throw new Error(`Error: ${error.message}`);
    }
  }
};

// Fetch live prices via Server-Sent Events - using named export
export const fetchLivePrices = (onMessage, onError) => {
  console.log('Connecting to price stream...');
  
  const eventSource = new EventSource(`${API_BASE_URL}/stream/prices`);
  
  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      console.log('Price update received:', data);
      onMessage(data);
    } catch (error) {
      console.error('Error parsing SSE message:', error);
    }
  };

  eventSource.onopen = () => {
    console.log('SSE connection opened successfully');
  };

  eventSource.onerror = (error) => {
    console.error('SSE connection error:', error);
    if (onError) {
      onError(error);
    }
  };

  return eventSource;
};

// Optional: You can also add a default export if needed
// but for now, we're using named exports only