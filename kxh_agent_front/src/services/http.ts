import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE as string,
  timeout: 30000
})

http.interceptors.response.use(
  (res) => res,
  (err) => Promise.reject(err)
)

export default http






