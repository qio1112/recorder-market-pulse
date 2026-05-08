<template>
  <section class="password-page">
    <form class="password-card" @submit.prevent="submitResetPassword">
      <div class="card-head">
        <h1>Reset Password</h1>
        <p class="subtext">Choose a new password for your account.</p>
      </div>
      <label class="field">
        <span>New Password</span>
        <input v-model="newPassword" type="password" autocomplete="new-password" />
      </label>
      <label class="field">
        <span>Confirm New Password</span>
        <input v-model="confirmPassword" type="password" autocomplete="new-password" />
      </label>
      <p v-if="error" class="message error">{{ error }}</p>
      <p v-if="success" class="message success">{{ success }}</p>
      <base-button mode="primary" :disabled="isSubmitting || Boolean(success)">
        {{ isSubmitting ? 'Updating...' : 'Reset Password' }}
      </base-button>
      <router-link class="secondary-link" to="/login">Back to login</router-link>
    </form>
  </section>
</template>

<script>
import { resetPassword } from '../api/UserService.js'

export default {
  name: 'ResetPasswordPage',
  data() {
    return {
      newPassword: '',
      confirmPassword: '',
      error: '',
      success: '',
      isSubmitting: false
    }
  },
  computed: {
    token() {
      return this.$route.query.token || '';
    }
  },
  methods: {
    validateForm() {
      if (!this.token) return 'Password reset token is missing.';
      if (!this.newPassword) return 'New password is required.';
      if (this.newPassword.length < 8) return 'New password must be at least 8 characters long.';
      if (this.newPassword !== this.confirmPassword) return 'Confirm password does not match the new password.';
      return '';
    },
    async submitResetPassword() {
      this.error = '';
      this.success = '';
      const validationError = this.validateForm();
      if (validationError) {
        this.error = validationError;
        return;
      }
      this.isSubmitting = true;
      try {
        this.success = await resetPassword(this.token, this.newPassword);
        this.newPassword = '';
        this.confirmPassword = '';
      } catch (error) {
        this.error = error?.response?.data || 'Failed to reset password.';
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
