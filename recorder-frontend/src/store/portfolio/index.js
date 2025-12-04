import { getStockDailyHistoryData } from '../../api/PortfolioService.js'

export default {
  namespaced: true,
  state() {
    return {
      // spyData: null,
      symbols: [],
      stockData: {},  // { symbol: "ABC", data: {...} }
      portfolioData: {}
    };
  },
  mutations: {
    setStockData(state, payload) {
      state.symbols = payload.symbols;
      state.stockData = payload.stockData;
    },
    setPortfolioData(state, payload) {
      state.portfolioDate = payload
    }
  },
  actions: {
    async updateStockHistoryData(context, symbols) {
      if (!symbols) {
        return ;
      }
      const data = await getStockDailyHistoryData(symbols);
      context.commit('setStockData', {symbols: symbols, stockData: data});
    },
    updatePortfolioData(context, data) {
      context.commit('setPortfolioData', data);
    }
  },
  getters: {
    getStockDailyHistoryData(state) {
      return state.stockData;
    }
  }
}