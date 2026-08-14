import axios from 'axios'

/** @type {string} localStorage保存自定义阅读http服务接口的键值 */
export const baseURL_localStorage_key = 'remoteUrl'
/** @type {string} localStorage保存Web访问令牌的键值 */
export const token_localStorage_key = 'webToken'
const SECOND = 1000

const ajax = axios.create({
  baseURL:
    import.meta.env.VITE_API ||
    localStorage.getItem(baseURL_localStorage_key) ||
    location.origin,
  timeout: 120 * SECOND,
})

/** 从 URL 参数或 localStorage 读取 Web 访问令牌 */
export const resolveToken = (): string =>
  new URLSearchParams(location.search).get('token') ||
  localStorage.getItem(token_localStorage_key) ||
  ''

ajax.interceptors.request.use((config) => {
  const token = resolveToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default ajax
