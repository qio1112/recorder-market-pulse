<template>
  <header>
    <h1><router-link to="/">REC</router-link></h1>
    <ul v-if="isUserAuthenticated">
      <li><router-link to="/records">Records</router-link></li>
      <li><router-link to="/records/calendar">Calendar</router-link></li>
      <li><router-link to="/add-record">Add Record</router-link></li>
      <li><router-link to="/portfolio">Portfolio</router-link></li>
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
  background-color: #00768d;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  gap: 0.5rem;
}

header a {
  text-decoration: none;
  color: #ffffff;
  display: inline-block;
  padding: 0.65rem 1.1rem;
  border: 1px solid transparent;
  border-radius: 4px
}

.nav-action {
  background: none;
  border: 1px solid transparent;
  color: #ffffff;
  padding: 0.65rem 1.1rem;
  font: inherit;
  cursor: pointer;
}

a:active,
a:hover,
a.router-link-active,
.nav-action:hover,
.nav-action:active,
.nav-action:focus-visible {
  border: 1px solid #63e6c8;
}

h1 {
  margin: 0;
}

h1 a {
  color: white;
  margin: 0;
}

h1 a:hover,
h1 a:active,
h1 a.router-link-active {
  border-color: transparent;
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
  gap: 0.35rem;
  margin-left: auto;
  overflow-x: auto;
  width: 100%;
  flex-wrap: wrap;
}

li {
  margin: 0;
}

.nav-action {
  border-radius: 4px;
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
</style>
