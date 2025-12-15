import http from './http'
import { convertListHistoryToMap } from '../utils/portfolioUtils.js'

export async function getSPYData() {
  try {
    const body = new RunUpdateStockScriptRequest();
    const response = await http.post("/stock-data/get-daily-history", body);
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
    const response = await http.post("/stock-data/get-daily-history", body);
    console.log('api response: ', response);
    const invalidSymbols = response.data.InvalidSymbols;
    return { data: convertListHistoryToMap(response.data.Data), invalidSymbols: invalidSymbols };
  } catch(error) {
    console.log(error);
    return null;
  }
}

export class RunUpdateStockScriptRequest{
  constructor({
    // jobName = 'get_stock_price_day_history_json',
    symbols = ['SPY'],
  } = {}) {
    // this.jobName = jobName;
    this.symbols = symbols;
  }

  toApi() {
    return {
      // jobName: this.jobName,
      symbols: this.symbols
    }
  }
}

// function toCommaString(input) {
//   if (Array.isArray(input)) {
//     return input.join(",");
//   }
//   if (typeof input === "string") {
//     return input;
//   }
//   return "";
// }