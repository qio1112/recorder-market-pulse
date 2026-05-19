import { authenticate, getUserInfo, parseJwtInfo } from '../../api/UserService.js'

const OPTION_HISTORY_SELECTION_STORAGE_KEY = 'recorder.optionHistory.selectedOptions'
const LLM_CHAT_HISTORY_STORAGE_KEY = 'recorder.llmChat.messages'

export default {
  namespaced: true,
  state() {
    const existingToken = localStorage.getItem('token');
    const tokenInfo = parseJwtInfo(existingToken);
    return {
      isAuthenticated: !tokenInfo.expired,
      jwtToken: tokenInfo.token,
      username: tokenInfo.username,
      isAdmin: false,
      userInfoLoaded: false
    };
  },
  mutations: {
    userLogin(state, payload) {
      state.isAuthenticated = true;
      state.jwtToken = payload.jwtToken;
      state.username = payload.username;
      state.isAdmin = false;
      state.userInfoLoaded = false;
    },
    userLogout(state) {
      state.isAuthenticated = false;
      state.jwtToken = '';
      state.username = '';
      state.isAdmin = false;
      state.userInfoLoaded = false;
    },
    setUserInfo(state, payload) {
      state.username = payload?.username || state.username;
      state.isAdmin = payload?.isAdmin === true || payload?.isAdmin === 'true';
      state.userInfoLoaded = true;
    }
  },
  actions: {
    async authenticateUser(context, payload) {
      const jwtToken = await authenticate(payload.username, payload.password);
      if (jwtToken) {
        localStorage.setItem('token', jwtToken);
        localStorage.removeItem(OPTION_HISTORY_SELECTION_STORAGE_KEY);
        localStorage.removeItem(LLM_CHAT_HISTORY_STORAGE_KEY);
        context.dispatch('optionHistory/clearCache', null, { root: true });
        context.commit('userLogin', {...payload, jwtToken: jwtToken});
        await context.dispatch('loadUserInfo');
      } else {
        localStorage.removeItem(OPTION_HISTORY_SELECTION_STORAGE_KEY);
        localStorage.removeItem(LLM_CHAT_HISTORY_STORAGE_KEY);
        context.dispatch('optionHistory/clearCache', null, { root: true });
        context.commit('userLogout');
      }
    },
    logoutUser(context) {
      localStorage.removeItem(OPTION_HISTORY_SELECTION_STORAGE_KEY);
      localStorage.removeItem(LLM_CHAT_HISTORY_STORAGE_KEY);
      context.dispatch('optionHistory/clearCache', null, { root: true });
      context.commit('userLogout');
      localStorage.setItem('token', '')
    },
    async loadUserInfo(context) {
      if (!context.state.isAuthenticated) {
        localStorage.removeItem(LLM_CHAT_HISTORY_STORAGE_KEY);
        context.commit('userLogout');
        return null;
      }
      const info = await getUserInfo();
      if (info) {
        context.commit('setUserInfo', info);
      }
      return info;
    }
  },
  getters: {
    isUserAuthenticated(state) {
      return state.isAuthenticated;
    },
    getJwtToken(state) {
      return state.jwtToken;
    },
    isAdmin(state) {
      return state.isAdmin;
    },
    isUserInfoLoaded(state) {
      return state.userInfoLoaded;
    }
  }
}
