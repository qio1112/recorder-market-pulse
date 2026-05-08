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
  </section>
</template>

<script>
import { getUserInfo } from '../api/UserService.js'

export default {
  name: 'UserAccountInfo',
  data() {
    return {
      user: null,
      isLoading: false
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
    }
  }
}
</script>

<style scoped>
.account-info {
  max-width: 640px;
  margin: 1.5rem auto;
  padding: 1rem;
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
</style>
