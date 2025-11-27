import { createStore } from 'vuex'
import recordsModule from './records/index.js'
import userModule from './user/index.js'

export default createStore({
  modules: {
    user: userModule,
    records: recordsModule
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
