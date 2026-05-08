<template>
  <section class="login-page">
    <form class="login-card" @submit.prevent="submitLoginForm">
      <div class="card-head">
        <p class="eyebrow">Welcome back</p>
        <h1>Login</h1>
        <p class="subtext">Access your recorder dashboard</p>
      </div>
      <div class="field">
        <label for="username">User Name</label>
        <input type="text" id="username" v-model.trim="username" placeholder="Enter your username" />
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input type="password" id="password" v-model.trim="password" placeholder="Enter your password" />
      </div>
      <p class="error" v-if="!isFormValid">Username and password cannot be empty.</p>
      <p class="error" v-if="!isAuthenticationValid">Invalid user name or password.</p>
      <base-button mode="primary">Login</base-button>
    </form>
  </section>
</template>

<script>
export default {
  data() {
    return {
      username: '',
      password: '',
      isFormValid: true,
      isAuthenticationValid: true
    }
  },
  methods: {
    validateForm() {
      this.isAuthenticationValid = true;
      this.isFormValid = this.username.length > 0 && this.password.length > 0;
    },
    async submitLoginForm() {
      this.validateForm();
      if (!this.isFormValid) {
        return;
      }
      await this.$store.dispatch('user/authenticateUser', {
        username: this.username,
        password: this.password
      });
      // const jwtToken = this.$store.getters['user/jwtToken'];
      this.isAuthenticationValid = this.$store.getters['user/isUserAuthenticated'];
      if (this.isAuthenticationValid) {
        this.$router.replace('/');
      }
    }
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #0f2436 0%, #12314a 100%);
  padding: 1.5rem;
}

.login-card {
  width: min(440px, 96vw);
  background: #ffffff;
  border-radius: 4px;
  padding: 2rem 1.75rem;
  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.22);
  border: 1px solid #e4ecf3;
  display: flex;
  flex-direction: column;
  gap: 1.3rem;
}

.card-head {
  text-align: center;
}

.eyebrow {
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 0.8rem;
  color: #597593;
  margin-bottom: 0.25rem;
}

.subtext {
  color: #597593;
  font-size: 0.95rem;
  margin-top: 0.35rem;
}

h1 {
  margin: 0;
  font-size: 2rem;
  color: #0f2f46;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

label {
  font-weight: 600;
  color: #0f2f46;
}

input {
  width: 100%;
  border-radius: 4px;
  border: 1px solid #c7d5e4;
  padding: 0.85rem 1rem;
  font-size: 1rem;
  background: #fff;
  color: #0f2f46;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

input:focus {
  outline: none;
  border-color: #3da7ff;
  box-shadow: 0 0 0 3px rgba(61, 167, 255, 0.12);
}

.error {
  margin: -0.5rem 0 0;
  color: #d64045;
  font-weight: 600;
  font-size: 0.95rem;
  background: rgba(214, 64, 69, 0.08);
  border: 1px solid rgba(214, 64, 69, 0.18);
  padding: 0.65rem 0.75rem;
  border-radius: 4px;
}

:deep(.primary) {
  width: 100%;
  padding: 0.9rem 1rem;
  border-radius: 4px;
  border: none;
  font-weight: 700;
  letter-spacing: 0.01em;
  background: linear-gradient(120deg, #3da7ff, #5ee4b1);
  box-shadow: 0 10px 24px rgba(61, 167, 255, 0.25);
  transition: box-shadow 0.2s ease, filter 0.2s ease;
}

:deep(.primary:hover),
:deep(.primary:active) {
  box-shadow: 0 12px 28px rgba(61, 167, 255, 0.3);
  filter: brightness(1.01);
}
</style>
