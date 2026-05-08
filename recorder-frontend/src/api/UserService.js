import http from './http'

// Authenticate the user and return the token from the response payload.
export async function authenticate(username, password) {
  try {
    const response = await http.post('/auth/authenticate', {
      username,
      password,
    });
    const token = response.data.token
    console.info(`user ${username} successfully logged in.`)
    return token;
  } catch (error) {
    return false;
  }
}

export function parseJwtInfo(token) {
  try {
    const base64 = token.split('.')[1]
    const json = atob(base64.replace(/-/g, '+').replace(/_/g, '/'))
    const payload = JSON.parse(json)
    
    const exp = payload.exp || null
    const expDate = exp ? new Date(exp * 1000) : null
    const expired = exp ? Date.now() >= exp * 1000 : true
    const username = payload.sub

    return {
      token: token,
      payload: payload,
      exp: exp,
      expDate: expDate,
      expired: expired,
      username: username
    }
  } catch (e) {
    console.error('Invalid JWT token:', e)
    return {
      token: token,
      payload: null,
      exp: null,
      expDate: null,
      expired: true,
      username: null
    }
  }
}

export function isTokenExpired(token) {
  const tokenInfo = parseJwtInfo(token);
  return tokenInfo.expired;
}

export async function getUserInfo() {
  try {
    const response = await http.get('/auth/user-info');
    return response.data;
  } catch (error) {
    return null;
  }
}

export async function changePassword(currentPassword, newPassword) {
  const response = await http.post('/auth/change-password', {
    currentPassword,
    newPassword,
  });
  return response.data;
}

export async function requestPasswordReset(email) {
  const response = await http.post('/auth/forgot-password', {
    email,
  });
  return response.data;
}

export async function resetPassword(token, newPassword) {
  const response = await http.post('/auth/reset-password', {
    token,
    newPassword,
  });
  return response.data;
}
  
