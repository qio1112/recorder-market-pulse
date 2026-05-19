import http from './http'

export async function createMarketNewsSummaryRecord() {
  const response = await http.post('/admin-tools/market-news-summary-record');
  return response.data;
}
