<template>
  <section class="account-info">
    <div class="card">
      <div class="card-head">
        <h1>Account</h1>
        <p class="muted">Profile details from your account</p>
      </div>
      <div v-if="isLoading" class="muted">Loading…</div>
      <div v-else-if="user" class="info-grid">
        <div class="info-item">
          <span class="label">Username</span>
          <span class="value">{{ user.username }}</span>
        </div>
        <div class="info-item">
          <span class="label">Email</span>
          <span class="value">{{ user.email }}</span>
        </div>
        <div class="info-item">
          <span class="label">Admin</span>
          <span class="pill" :class="user.isAdmin === 'true' || user.isAdmin === true ? 'pill-yes' : 'pill-no'">
            {{ user.isAdmin }}
          </span>
        </div>
        <div class="info-item">
          <span class="label">Created</span>
          <span class="value">{{ formatDateTime(user.creationTime) }}</span>
        </div>
      </div>
      <div v-else class="muted">Unable to load user info.</div>
    </div>

    <div class="card password-card">
      <div class="card-head">
        <h2>Change Password</h2>
      </div>
      <form class="password-form" @submit.prevent="submitPasswordChange">
        <label class="form-field">
          <span>Current Password</span>
          <input v-model="passwordForm.currentPassword" type="password" autocomplete="current-password" />
        </label>
        <label class="form-field">
          <span>New Password</span>
          <input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" />
        </label>
        <label class="form-field">
          <span>Confirm New Password</span>
          <input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" />
        </label>
        <p v-if="passwordError" class="message error">{{ passwordError }}</p>
        <p v-if="passwordSuccess" class="message success">{{ passwordSuccess }}</p>
        <button type="submit" class="submit-button" :disabled="isUpdatingPassword">
          {{ isUpdatingPassword ? 'Updating...' : 'Update Password' }}
        </button>
      </form>
    </div>
  </section>
</template>

<script>
import { changePassword, getUserInfo } from '../api/UserService.js'

export default {
  name: 'UserAccountInfo',
  data() {
    return {
      user: null,
      isLoading: false,
      isUpdatingPassword: false,
      passwordError: '',
      passwordSuccess: '',
      passwordForm: {
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
    }
  },
  async created() {
    this.isLoading = true;
    this.user = await getUserInfo();
    this.isLoading = false;
  },
  methods: {
    formatDateTime(iso) {
      try {
        return new Date(iso).toLocaleString();
      } catch (e) {
        return iso;
      }
    },
    validatePasswordForm() {
      if (!this.passwordForm.currentPassword) {
        return 'Current password is required.';
      }
      if (!this.passwordForm.newPassword) {
        return 'New password is required.';
      }
      if (this.passwordForm.newPassword.length < 8) {
        return 'New password must be at least 8 characters long.';
      }
      if (this.passwordForm.newPassword !== this.passwordForm.confirmPassword) {
        return 'Confirm password does not match the new password.';
      }
      return '';
    },
    async submitPasswordChange() {
      this.passwordError = '';
      this.passwordSuccess = '';
      const validationError = this.validatePasswordForm();
      if (validationError) {
        this.passwordError = validationError;
        return;
      }
      this.isUpdatingPassword = true;
      try {
        await changePassword(this.passwordForm.currentPassword, this.passwordForm.newPassword);
        this.passwordSuccess = 'Password updated successfully.';
        this.passwordForm = {
          currentPassword: '',
          newPassword: '',
          confirmPassword: ''
        };
      } catch (error) {
        this.passwordError = error?.response?.data || 'Failed to update password.';
      } finally {
        this.isUpdatingPassword = false;
      }
    }
  }
}
</script>

<style scoped>
.account-info {
  max-width: 640px;
  margin: 1.5rem auto;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.card {
  border: 1px solid #e5e8ed;
  border-radius: 4px;
  background: #fff;
  padding: 1rem 1.25rem;
  box-shadow: 0 6px 18px rgba(16, 42, 67, 0.05);
}

.card-head {
  margin-bottom: 0.75rem;
}

.card-head h2 {
  color: #102a43;
  font-size: 1.1rem;
  margin: 0;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.75rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.65rem 0.75rem;
  border: 1px solid #e5e8ed;
  border-radius: 4px;
  background: #f9fbfd;
}

.label {
  font-size: 0.9rem;
  color: #52606d;
}

.value {
  font-weight: 700;
  color: #102a43;
}

.pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.25rem 0.6rem;
  border-radius: 4px;
  font-weight: 700;
  font-size: 0.9rem;
}

.pill-yes {
  background: #e0f7ec;
  color: #0f7b4d;
}

.pill-no {
  background: #fdecea;
  color: #c0392b;
}

.muted {
  color: #52606d;
}

.password-card {
  padding-top: 0.9rem;
}

.password-form {
  display: grid;
  gap: 0.7rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  color: #52606d;
  font-size: 0.88rem;
  font-weight: 700;
}

.form-field input {
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  color: #102a43;
  font: inherit;
  font-size: 0.92rem;
  padding: 0.55rem 0.65rem;
}

.form-field input:focus {
  border-color: #2f80ed;
  outline: none;
}

.message {
  border-radius: 4px;
  font-size: 0.84rem;
  margin: 0;
  padding: 0.5rem 0.65rem;
}

.message.error {
  background: #fff5f5;
  border: 1px solid #f2b8b5;
  color: #b42318;
}

.message.success {
  background: #e0f7ec;
  border: 1px solid #b7ebd0;
  color: #0f7b4d;
}

.submit-button {
  justify-self: start;
  border: 1px solid #0f4c81;
  border-radius: 4px;
  background: #0f4c81;
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 700;
  padding: 0.55rem 0.8rem;
}

.submit-button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}
</style>
