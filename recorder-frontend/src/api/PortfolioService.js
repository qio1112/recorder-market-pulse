import http from './http'
import { convertListHistoryToMap } from '../utils/portfolioUtils.js'

export async function getSPYData() {
  try {
    const body = new RunUpdateStockScriptRequest();
    const response = await http.post("/run-script/update_stock_data", body);
    // console.log(response);
    return response.data.Data[0];
  } catch(error) {
    console.log(error);
    return null;
  }
}

export async function getStockDailyHistoryData(symbols) {
  try {
    const body = new RunUpdateStockScriptRequest({symbols: symbols});
    const response = await http.post("/run-script/update_stock_data", body);
    console.log('api response: ', response);
    return convertListHistoryToMap(response.data.Data);
  } catch(error) {
    console.log(error);
    return null;
  }
}

export class RunUpdateStockScriptRequest{
  constructor({
    jobName = 'get_stock_price_day_history_json',
    symbols = ['SPY'],
  } = {}) {
    this.jobName = jobName;
    this.symbols = toCommaString(symbols);
  }

  toApi() {
    return {
      jobName: this.jobName,
      symbols: this.symbols
    }
  }
}

function toCommaString(input) {
  if (Array.isArray(input)) {
    return input.join(",");
  }
  if (typeof input === "string") {
    return input;
  }
  return "";
}