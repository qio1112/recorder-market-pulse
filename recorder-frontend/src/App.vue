<template>
  <div class="app-shell">
    <the-header></the-header>
    <main class="app-main">
      <router-view v-slot="slotProps">
        <transition name="route" mode="out-in">
          <component :is="slotProps.Component"></component>
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script>
import TheHeaderVue from './components/TheHeader.vue'

export default {
  components: {
    TheHeader: TheHeaderVue
  }
}
</script>

<style>
@import url("https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap");

* {
  box-sizing: border-box;
}

html {
  font-family: "Roboto", sans-serif;
  background: #edf3f8;
}

body {
  margin: 0;
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f4f8fb 0%, #edf3f8 42%, #e7eef6 100%);
  color: #1f2933;
}

#app,
.app-shell {
  min-height: 100vh;
}

.app-shell {
  background:
    radial-gradient(circle at top left, rgba(15, 76, 129, 0.08), transparent 26rem),
    linear-gradient(180deg, rgba(255, 255, 255, 0.68), rgba(237, 243, 248, 0.92));
}

.app-main {
  min-height: calc(100vh - 4.2rem);
  padding: 0.1px 0 2rem;
}

.route-enter-from {
  opacity: 0;
  transform: translateY(-30px);
}

.route-leave-to {
  opacity: 0;
  transform: translateY(30px);
}

.route-enter-active {
  transition: all 0.3s ease-out;
}

.route-leave-active {
  transition: all 0.3s ease-in;
}

.route-enter-to
.route-leave-from {
  opacity: 1;
  transform: translateY(0);
}
</style>
