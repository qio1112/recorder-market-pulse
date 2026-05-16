export function validateTrade(metaObj) {
  if (!metaObj) return false;
      // normalize keys to lower
      const normalized = Object.keys(metaObj).reduce((acc, key) => {
        acc[key.toLowerCase()] = metaObj[key];
        return acc;
      }, {});
      const symbol = normalized.symbol;
      const price = Number(normalized.price);
      const shares = Number(normalized.shares);
      const action = normalized.action ? String(normalized.action).toLowerCase() : '';
      const date = normalized.date ? String(normalized.date) : '';
      const dateOk = /^\d{4}-\d{2}-\d{2}$/.test(date);
      const actionOk = action === 'buy' || action === 'sell';
      const priceOk = Number.isFinite(price) && price > 0;
      const sharesOk = Number.isFinite(shares) && shares > 0;
      return Boolean(symbol) && priceOk && sharesOk && dateOk && actionOk;
}

export function recordGroupBySymbolAndSort(trades) {
  const grouped = {};

  // Group trades by symbol
  for (const t of trades) {
    const symbol = t.symbol;
    if (!grouped[symbol]) {
      grouped[symbol] = [];
    }
    // push a shallow copy so we don't mutate the original input
    grouped[symbol].push({ ...t });
  }

  // Sort each symbol's trades by date (assumes YYYY-MM-DD)
  for (const symbol of Object.keys(grouped)) {
    grouped[symbol].sort((a, b) => a.date.localeCompare(b.date));
  }

  return grouped;
}

export function enrichAccumulativeTradeData(tradesBySymbol, historyData) {
  if (!tradesBySymbol) {
    return {};
  }
  const enriched = {};
  for (const [symbol, trades] of Object.entries(tradesBySymbol)) {
    const enrichedForSymbol = enrichAccumulativeTradeDataForSymbol(trades, historyData[symbol]);
    // if (enrichedForSymbol.shares.at(-1) !== 0) { // if holding 0 shares now, no need to show it
      enriched[symbol] = enrichedForSymbol;
    // }
  }
  return enriched;
}

export function calculateTotalPortfolioData(enrichedList) {
  if (!enrichedList || typeof enrichedList !== 'object') {
    return { dates: [], totalPortfolio: [] };
  }

  const dateSet = new Set();
  const lookups = {};

  for (const [symbol, data] of Object.entries(enrichedList)) {
    const dates = data?.dates;
    const totals = data?.totalPortfolio;
    if (!Array.isArray(dates) || !Array.isArray(totals)) continue;
    const map = new Map();
    for (let i = 0; i < dates.length; i++) {
      map.set(dates[i], Number(totals[i]) || 0);
      dateSet.add(dates[i]);
    }
    lookups[symbol] = map;
  }

  const allDates = Array.from(dateSet).sort();
  const totalPortfolio = allDates.map((date) => {
    let sum = 0;
    for (const map of Object.values(lookups)) {
      sum += map.get(date) || 0;
    }
    return sum;
  });

  return { dates: allDates, totalPortfolio };
}

export function aggregateMetricAcrossSymbols(enrichedData, metric) {
  if (!enrichedData || typeof enrichedData !== 'object') {
    return { dates: [], values: [] };
  }

  const dateSet = new Set();
  const lookups = [];

  Object.values(enrichedData).forEach((data) => {
    const dates = data?.dates;
    const values = data?.[metric];
    if (!Array.isArray(dates) || !Array.isArray(values)) return;
    const map = new Map();
    for (let i = 0; i < dates.length; i++) {
      map.set(dates[i], Number(values[i]) || 0);
      dateSet.add(dates[i]);
    }
    lookups.push(map);
  });

  const allDates = Array.from(dateSet).sort();
  const aggregated = allDates.map((d) =>
    lookups.reduce((sum, map) => sum + (map.get(d) || 0), 0)
  );

  return { dates: allDates, values: aggregated };
}

export function enrichAccumulativeTradeDataForSymbol(trades, historyData) {
  if (!trades) {
    return {};
  }

  const allDailyClose = historyData['Close'];
  const allHistoryDates = historyData['Datetime'];
  // console.log(dailyClose);
  const dateBuffer = 3;
  const earliestDate = trades[0].date;
  const earliestDateIndex = allHistoryDates.findIndex(d => d >= earliestDate);
  const startDateIndex = earliestDateIndex >= dateBuffer ? earliestDateIndex - dateBuffer : 0;

  // dates that will be used in final result
  const dates = allHistoryDates.slice(startDateIndex);
  const dailyClose = allDailyClose.slice(startDateIndex);

  let totalShares = 0;      // current position size
  let totalCost = 0;        // cost basis of current position (in $)
  let cash = 0;  // cash balance
  let realizedPnL = 0;      // cumulative realized P&L
  let averageCostPerShare = 0;

  let tradeIndex = 0;

  const result = {
    dates: dates,
    actions: [], // e.g. [null, [trade1, trade2], null, null]
    closePrice: dailyClose,
    shares: [],  // number of shares holding eod
    cashFlow: [], 
    cash: [],  
    averageCostPerShare: [], // average cost per share of the shares holding eod
    totalCost: [], // total cost of current holding shares
    realizedPnL: [],  // accumulative realized P&L eod
    unrealizedPnL: [],  // unrealizedPnL eod, closePrice * numShares - totalCost
    totalStockValue: [], // stock value eod, closePrice * numShares
    totalPortfolio: [] // stock value + cash
  };

  for (let i = 0; i < dates.length; i ++) {
    const date = dates[i];
    const closePrice = dailyClose[i];
    let action = null;
    let cashFlow = 0;
    
    while (tradeIndex < trades.length && trades[tradeIndex].date === date) { // there can be multiple trades on same day
      const nextTrade = trades[tradeIndex];
      if (action == null) { action = [];}
      const tradeSharesSigned = nextTrade.action === 'buy' ? Number(nextTrade.shares) : -Number(nextTrade.shares);
      action.push({...nextTrade, signedShares: tradeSharesSigned});
      totalShares += tradeSharesSigned;
      cashFlow = -tradeSharesSigned * nextTrade.price;
      cash += cashFlow;
      if (nextTrade.action === 'buy') {
        totalCost += nextTrade.price * tradeSharesSigned;
        averageCostPerShare = totalCost / totalShares;
      } else { // sell, averageCostPerShare remains the same
        totalCost += averageCostPerShare * tradeSharesSigned;
        realizedPnL -= (nextTrade.price - averageCostPerShare) * tradeSharesSigned;
      }
      tradeIndex += 1;
    }
    let totalStockValue = closePrice * totalShares;
    let unrealizedPnL = totalStockValue - totalCost;
    let totalPortfolio = totalStockValue + cash;
    result.actions.push(action);
    result.shares.push(totalShares);
    result.cashFlow.push(cashFlow);
    result.cash.push(cash);
    result.averageCostPerShare.push(averageCostPerShare);
    result.totalCost.push(totalCost);
    result.realizedPnL.push(realizedPnL);
    result.unrealizedPnL.push(unrealizedPnL);
    result.totalStockValue.push(totalStockValue);
    result.totalPortfolio.push(totalPortfolio);
  }

  return result;
}

export function convertListHistoryToMap(historyDataList) {
  const result = historyDataList.reduce((acc, item) => {
    acc[item.Symbol] = item;   // use symbol as key
    return acc;
  }, {});
  return result;
}
