import { authenticate, parseJwtInfo } from '../../api/UserService.js'

const OPTION_HISTORY_SELECTION_STORAGE_KEY = 'recorder.optionHistory.selectedOptions'

export default {
  namespaced: true,
  state() {
    const existingToken = localStorage.getItem('token');
    const tokenInfo = parseJwtInfo(existingToken);
    return {
      isAuthenticated: !tokenInfo.expired,
      jwtToken: tokenInfo.token,
      username: tokenInfo.username
    };
  },
  mutations: {
    userLogin(state, payload) {
      state.isAuthenticated = true;
      state.jwtToken = payload.jwtToken;
      state.username = payload.username;
    },
    userLogout(state) {
      state.isAuthenticated = false;
      state.jwtToken = '';
      state.username = '';
    }
  },
  actions: {
    async authenticateUser(context, payload) {
      const jwtToken = await authenticate(payload.username, payload.password);
      if (jwtToken) {
        localStorage.setItem('token', jwtToken);
        localStorage.removeItem(OPTION_HISTORY_SELECTION_STORAGE_KEY);
        context.dispatch('optionHistory/clearCache', null, { root: true });
        context.commit('userLogin', {...payload, jwtToken: jwtToken});
      } else {
        localStorage.removeItem(OPTION_HISTORY_SELECTION_STORAGE_KEY);
        context.dispatch('optionHistory/clearCache', null, { root: true });
        context.commit('userLogout');
      }
    },
    logoutUser(context) {
      localStorage.removeItem(OPTION_HISTORY_SELECTION_STORAGE_KEY);
      context.dispatch('optionHistory/clearCache', null, { root: true });
      context.commit('userLogout');
      localStorage.setItem('token', '')
    }
  },
  getters: {
    isUserAuthenticated(state) {
      return state.isAuthenticated;
    },
    getJwtToken(state) {
      return state.jwtToken;
    }
  }
}
