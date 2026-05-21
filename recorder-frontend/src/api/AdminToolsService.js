import http from './http'

export async function createMarketNewsSummaryRecord() {
  const response = await http.post('/admin-tools/market-news-summary-record');
  return response.data;
}

export async function getJobDashboard() {
  const response = await http.get('/admin-tools/jobs/dashboard');
  return response.data;
}

export async function listJobExecutions(params = {}) {
  const response = await http.get('/admin-tools/jobs/executions', { params });
  return response.data;
}

export async function createJobConfig(payload) {
  const response = await http.post('/admin-tools/jobs/configs', payload);
  return response.data;
}

export async function updateJobConfig(id, payload) {
  const response = await http.put(`/admin-tools/jobs/configs/${id}`, payload);
  return response.data;
}

export async function deleteJobConfig(id) {
  const response = await http.delete(`/admin-tools/jobs/configs/${id}`);
  return response.data;
}

export async function triggerJob(id) {
  const response = await http.post(`/admin-tools/jobs/configs/${id}/trigger`);
  return response.data;
}
