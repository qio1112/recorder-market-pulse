import { getStockDailyHistoryData } from '../../api/PortfolioService.js'

export default {
  namespaced: true,
  state() {
    return {
      // spyData: null,
      symbols: [],
      invalidSymbols: [],
      stockData: {},  // { symbol: "ABC", data: {...} }
      portfolioData: {}
    };
  },
  mutations: {
    setStockData(state, payload) {
      state.symbols = payload.symbols;
      state.invalidSymbols = payload.invalidSymbols;
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
      const validSymbols = Object.keys(data);
      const invalidSymbols = data.invalidSymbols;
      context.commit('setStockData', {symbols: validSymbols, 
                                      invalidSymbols: invalidSymbols, 
                                      stockData: data.data});
    },
    updatePortfolioData(context, data) {
      context.commit('setPortfolioData', data);
    }
  },
  getters: {
    getStockDailyHistoryData(state) {
      return state.stockData;
    },
    getInvalidSymbols(state) {
      return state.invalidSymbols;
    }
  }
}