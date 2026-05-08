const appEnv = (process.env.VUE_APP_APP_ENV || '').toUpperCase();
const isLocalBrowser = typeof window !== 'undefined'
  && ['localhost', '127.0.0.1'].includes(window.location.hostname);

export const API_BASE_URL = appEnv === 'DEV' || (!appEnv && isLocalBrowser)
  ? 'http://localhost:8080/api'
  : '/api';
