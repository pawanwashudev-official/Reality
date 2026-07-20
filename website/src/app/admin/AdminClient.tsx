'use client';

import React, { useState } from 'react';
import LoginClient from './LoginClient';
import AdminDashboard from './AdminDashboard';

export default function AdminClient() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  if (isAuthenticated) {
    return <AdminDashboard />;
  }

  return <LoginClient onAuthenticated={() => setIsAuthenticated(true)} />;
}
