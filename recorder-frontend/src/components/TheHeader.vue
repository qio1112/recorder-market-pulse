<template>
  <header>
    <h1><router-link to="/">REC</router-link></h1>
    <ul v-if="isUserAuthenticated">
      <li><router-link to="/records">Records</router-link></li>
      <li><router-link to="/calendar">Calendar</router-link></li>
      <li><router-link to="/add-record">Add Record</router-link></li>
      <li class="nav-dropdown">
        <button
          type="button"
          class="nav-action nav-dropdown-trigger"
          :class="{ 'router-link-active': isToolsActive }"
        >
          Tools
          <span class="dropdown-caret">▾</span>
        </button>
        <ul class="dropdown-menu">
          <li>
            <router-link to="/tools/portfolio">
              <span class="tool-title">Portfolio</span>
              <span class="tool-description">Holdings and performance</span>
            </router-link>
          </li>
          <li>
            <router-link to="/tools/option-return">
              <span class="tool-title">Option Return</span>
              <span class="tool-description">Options position analysis</span>
            </router-link>
          </li>
        </ul>
      </li>
      <li><router-link to="/account">Account</router-link></li>
      <li><button type="button" class="nav-action" @click="logoutUser">Logout</button></li>
    </ul>
  </header>
</template>

<script>
export default {
  computed: {
    isUserAuthenticated() {
      return this.$store.getters['user/isUserAuthenticated'];
    },
    isToolsActive() {
      return this.$route.path.startsWith('/tools/');
    }
  },
  methods: {
    logoutUser() {
      this.$store.dispatch('user/logoutUser');
      this.$router.replace('/login');
    }
  }
}
</script>

<style scoped>
header {
  width: 100%;
  box-sizing: border-box;
  background: #ffffff;
  border-bottom: 1px solid #d9e2ec;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  padding: 0.7rem 1.25rem;
  gap: 1rem;
}

header a {
  text-decoration: none;
  color: #334155;
  display: inline-block;
  padding: 0.55rem 0.8rem;
  border: 1px solid transparent;
  border-radius: 7px;
  font-size: 0.9rem;
  font-weight: 600;
}

.nav-action {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: none;
  border: 1px solid transparent;
  color: #334155;
  padding: 0.55rem 0.8rem;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  border-radius: 7px;
}

a:active,
a:hover,
a.router-link-active,
.nav-action.router-link-active,
.nav-action:hover,
.nav-action:active,
.nav-action:focus-visible {
  border-color: #cfe7ff;
  background: #eef6ff;
  color: #0f4c81;
}

h1 {
  margin: 0;
  line-height: 1;
}

h1 a {
  color: #0f4c81;
  margin: 0;
  padding: 0.45rem 0.6rem;
  border-radius: 8px;
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

h1 a:hover,
h1 a:active,
h1 a.router-link-active {
  border-color: transparent;
  background: transparent;
  color: #0f4c81;
}

header nav {
  width: 90%;
  margin: auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

header ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  align-items: center;
  gap: 0.2rem;
  margin-left: auto;
  overflow: visible;
  width: 100%;
  flex-wrap: wrap;
}

li {
  margin: 0;
}

.nav-dropdown {
  position: relative;
}

.nav-dropdown-trigger {
  display: inline-flex;
}

.dropdown-caret {
  color: #64748b;
  font-size: 0.75rem;
  line-height: 1;
}

.dropdown-menu {
  position: absolute;
  top: calc(100% + 0.45rem);
  left: 0;
  z-index: 20;
  display: none;
  min-width: 17rem;
  margin: 0;
  margin-left: 0;
  padding: 0.45rem;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.16);
  overflow: visible;
}

.dropdown-menu::before {
  content: "";
  position: absolute;
  top: -0.5rem;
  left: 0;
  right: 0;
  height: 0.5rem;
}

.dropdown-menu li {
  width: 100%;
}

.dropdown-menu a {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  width: 100%;
  box-sizing: border-box;
  padding: 0.7rem 0.8rem;
  white-space: nowrap;
}

.tool-title {
  color: inherit;
  font-size: 0.9rem;
  font-weight: 700;
}

.tool-description {
  color: #64748b;
  font-size: 0.76rem;
  font-weight: 500;
}

.nav-dropdown:hover .dropdown-menu,
.nav-dropdown:focus-within .dropdown-menu {
  display: block;
}

@media (min-width: 640px) {
  header {
    flex-wrap: nowrap;
  }
  header ul {
    flex-wrap: nowrap;
    width: auto;
  }
}

@media (max-width: 639px) {
  header {
    align-items: flex-start;
  }

  header ul {
    overflow-x: auto;
  }

  .dropdown-menu {
    position: static;
    display: none;
    width: 100%;
    margin-top: 0.25rem;
    box-shadow: none;
  }

  .dropdown-menu a {
    white-space: normal;
  }
}
</style>
