import axios from 'axios'
import { API_BASE_URL } from './config.js'
import store from '../store/index.js'

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

http.interceptors.request.use(
  (config) => {
    const token = store.getters['user/getJwtToken'];
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config
  },
  (error) => Promise.reject(error)
)

export default http
