import request from '../utils/request'

export const authApi = {
  login(data) {
    return request.post('/api/auth/login', data)
  },
  register(data) {
    return request.post('/api/auth/register', data)
  },
  logout() {
    return request.post('/api/auth/logout')
  }
}

export const userApi = {
  getProfile() {
    return request.get('/api/user/profile')
  },
  updateProfile(data) {
    return request.put('/api/user/profile', data)
  },
  changePassword(data) {
    return request.post('/api/user/change-password', data)
  }
}

export const agentApi = {
  getAll() {
    return request.get('/api/agents')
  },
  getPage(params) {
    return request.get('/api/agents/page', { params })
  },
  getById(id) {
    return request.get(`/api/agents/${id}`)
  },
  updateStatus(id, status) {
    return request.put(`/api/agents/${id}/status`, { status })
  },
  update(id, data) {
    return request.put(`/api/agents/${id}`, data)
  }
}

export const workflowApi = {
  getPage(params) {
    return request.get('/api/workflow/page', { params })
  },
  getById(id) {
    return request.get(`/api/workflow/${id}`)
  },
  getSteps(id) {
    return request.get(`/api/workflow/${id}/steps`)
  },
  getArtifacts(id) {
    return request.get(`/api/workflow/${id}/artifacts`)
  },
  cancel(id) {
    return request.post(`/api/workflow/${id}/cancel`)
  }
}

export const taskApi = {
  submit(userInput) {
    return request.post('/api/agent/generate', { userInput })
  },
  getResult(taskId) {
    return request.get(`/api/agent/task/${taskId}`)
  },
  saveProject(taskId) {
    return request.post(`/api/agent/task/${taskId}/save`)
  }
}

export const logApi = {
  getPage(params) {
    return request.get('/api/logs/page', { params })
  },
  getById(id) {
    return request.get(`/api/logs/${id}`)
  }
}

export const artifactApi = {
  download(id) {
    return request.get(`/api/artifacts/${id}/download`, { responseType: 'blob' })
  },
  getById(id) {
    return request.get(`/api/artifacts/${id}`)
  }
}
