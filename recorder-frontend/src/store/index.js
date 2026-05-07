import { createStore } from 'vuex'
import recordsModule from './records/index.js'
import userModule from './user/index.js'
import portfolioModule from './portfolio/index.js'
import optionHistoryModule from './optionHistory/index.js'

export default createStore({
  modules: {
    user: userModule,
    records: recordsModule,
    portfolio: portfolioModule,
    optionHistory: optionHistoryModule
  },
  state() {
  },
  getters: {
  },
  mutations: {
  },
  actions: {
  }
})
