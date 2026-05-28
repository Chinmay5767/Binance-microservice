import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { Navbar as BootstrapNavbar, Nav, Container } from 'react-bootstrap';
import { FaChartLine, FaWallet, FaSignOutAlt } from 'react-icons/fa';

const Navbar = ({ userInfo, onLogout }) => {
  const navigate = useNavigate();

  const handleLogout = () => {
    onLogout();
    navigate('/login');
  };

  return (
    <BootstrapNavbar bg="dark" variant="dark" expand="lg">
      <Container>
        <BootstrapNavbar.Brand href="/">
          <FaChartLine className="me-2" />
          Binance Tracker
        </BootstrapNavbar.Brand>
        <BootstrapNavbar.Toggle aria-controls="basic-navbar-nav" />
        <BootstrapNavbar.Collapse id="basic-navbar-nav">
          <Nav className="me-auto">
            <Nav.Link as={NavLink} to="/portfolio">
              <FaWallet className="me-1" />
              Portfolio
            </Nav.Link>
            <Nav.Link as={NavLink} to="/stream">
              <FaChartLine className="me-1" />
              Live Stream
            </Nav.Link>
          </Nav>
          <Nav className="ms-auto">
            {userInfo && (
              <>
                <span className="navbar-text text-light me-3">
                  Welcome, {userInfo.username || userInfo.emailId}
                </span>
                <Nav.Link onClick={handleLogout}>
                  <FaSignOutAlt className="me-1" />
                  Logout
                </Nav.Link>
              </>
            )}
          </Nav>
        </BootstrapNavbar.Collapse>
      </Container>
    </BootstrapNavbar>
  );
};

export default Navbar;