import { getOptionHistory } from '../../api/OptionHistoryService.js'

export default {
  namespaced: true,
  state() {
    return {
      historyByKey: {}
    };
  },
  mutations: {
    setOptionHistory(state, payload) {
      state.historyByKey[payload.key] = payload.history;
    },
    clearOptionHistory(state) {
      state.historyByKey = {};
    }
  },
  actions: {
    async loadOptionHistory(context, payload) {
      const key = `${payload.symbol}|${payload.expiry}|${payload.optionType}`;
      if (context.state.historyByKey[key]) {
        return context.state.historyByKey[key];
      }
      const history = await getOptionHistory(payload.symbol, payload.expiry, payload.optionType);
      context.commit('setOptionHistory', { key, history });
      return history;
    },
    clearCache(context) {
      context.commit('clearOptionHistory');
    }
  },
  getters: {
    getOptionHistoryByKey: (state) => (key) => {
      return state.historyByKey[key] || null;
    }
  }
}
