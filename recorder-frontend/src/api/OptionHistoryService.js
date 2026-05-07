import http from './http'

export async function getOptionSymbols() {
  const response = await http.get('/option-data/symbols');
  return response.data?.symbols || [];
}

export async function getOptionExpiries(symbol) {
  const response = await http.post('/option-data/expiries', { symbol });
  return response.data?.expiry_dates || response.data?.expiryDates || [];
}

export async function getOptionHistory(symbol, expiry, optionType) {
  const response = await http.post('/option-data/history', {
    symbol,
    expiry,
    optionType
  });
  return response.data;
}
