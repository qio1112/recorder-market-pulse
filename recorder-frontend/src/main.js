import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'

import BaseButton from './components/ui/BaseButton.vue'

const app = createApp(App)

app.component('base-button', BaseButton)

app.use(store).use(router).mount('#app')
