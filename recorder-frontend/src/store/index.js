import { createStore } from 'vuex'
import recordsModule from './records/index.js'
import userModule from './user/index.js'
import portfolioModule from './portfolio/index.js'

export default createStore({
  modules: {
    user: userModule,
    records: recordsModule,
    portfolio: portfolioModule
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
