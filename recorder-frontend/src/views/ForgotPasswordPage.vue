<template>
  <section class="password-page">
    <form class="password-card" @submit.prevent="submitForgotPassword">
      <div class="card-head">
        <h1>Forgot Password</h1>
        <p class="subtext">Enter the email from your account.</p>
      </div>
      <label class="field">
        <span>Email</span>
        <input v-model.trim="email" type="email" autocomplete="email" placeholder="name@example.com" />
      </label>
      <p v-if="error" class="message error">{{ error }}</p>
      <p v-if="success" class="message success">{{ success }}</p>
      <base-button mode="primary" :disabled="isSubmitting">Send Reset Link</base-button>
      <router-link class="secondary-link" to="/login">Back to login</router-link>
    </form>
  </section>
</template>

<script>
import { requestPasswordReset } from '../api/UserService.js'

export default {
  name: 'ForgotPasswordPage',
  data() {
    return {
      email: '',
      error: '',
      success: '',
      isSubmitting: false
    }
  },
  methods: {
    async submitForgotPassword() {
      this.error = '';
      this.success = '';
      if (!this.email) {
        this.error = 'Email is required.';
        return;
      }
      this.isSubmitting = true;
      try {
        this.success = await requestPasswordReset(this.email);
      } catch (error) {
        this.error = error?.response?.data || 'Failed to send password reset email.';
      } finally {
        this.isSubmitting = false;
      }
    }
  }
}
</script>

<style scoped>
.password-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #0f2436 0%, #12314a 100%);
  padding: 1.5rem;
}

.password-card {
  width: min(440px, 96vw);
  background: #ffffff;
  border: 1px solid #e4ecf3;
  border-radius: 4px;
  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.22);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 2rem 1.75rem;
}

.card-head {
  text-align: center;
}

h1 {
  color: #0f2f46;
  font-size: 1.8rem;
  margin: 0;
}

.subtext {
  color: #597593;
  font-size: 0.95rem;
  margin: 0.35rem 0 0;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  color: #0f2f46;
  font-weight: 600;
}

input {
  border: 1px solid #c7d5e4;
  border-radius: 4px;
  color: #0f2f46;
  font: inherit;
  font-size: 1rem;
  padding: 0.85rem 1rem;
}

input:focus {
  border-color: #3da7ff;
  box-shadow: 0 0 0 3px rgba(61, 167, 255, 0.12);
  outline: none;
}

.message {
  border-radius: 4px;
  font-size: 0.9rem;
  font-weight: 600;
  margin: 0;
  padding: 0.65rem 0.75rem;
}

.message.error {
  background: rgba(214, 64, 69, 0.08);
  border: 1px solid rgba(214, 64, 69, 0.18);
  color: #d64045;
}

.message.success {
  background: #e0f7ec;
  border: 1px solid #b7ebd0;
  color: #0f7b4d;
}

.secondary-link {
  color: #0f4c81;
  font-size: 0.9rem;
  font-weight: 700;
  text-align: center;
  text-decoration: none;
}

.secondary-link:hover {
  text-decoration: underline;
}
</style>
