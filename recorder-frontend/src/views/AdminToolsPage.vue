<template>
  <section class="admin-tools">
    <header class="page-header">
      <h1>Admin Tools</h1>
    </header>

    <div class="tools-list">
      <article class="tool-item">
        <div class="tool-copy">
          <h2>Daily Stock News Summary</h2>
          <p>
            Start a background job that creates a public market news summary record from tracked stock symbols.
          </p>
        </div>
        <div class="tool-action">
          <base-button
            mode="primary"
            :disabled="isCheckingLlm || noLlmConnection || isCreatingNewsSummary"
            @click="createNewsSummary"
          >
            {{ newsSummaryButtonText }}
          </base-button>
          <p
            v-if="newsSummaryMessage"
            class="status-message"
            :class="newsSummaryStatus"
          >
            {{ newsSummaryMessage }}
          </p>
          <p v-else-if="noLlmConnection" class="status-message failed">
            No LLM connection
          </p>
          <p v-else-if="isCheckingLlm" class="status-message muted">
            Checking LLM connection...
          </p>
        </div>
      </article>
    </div>
  </section>
</template>

<script>
import { createMarketNewsSummaryRecord } from '../api/AdminToolsService.js'
import { sendLlmChat } from '../api/LlmService.js'

const SYSTEM_MESSAGE = {
  role: 'system',
  content: 'You are a concise assistant for the Recorder admin user.'
}

export default {
  name: 'AdminToolsPage',
  data() {
    return {
      isCheckingLlm: true,
      noLlmConnection: false,
      isCreatingNewsSummary: false,
      newsSummaryMessage: '',
      newsSummaryStatus: ''
    }
  },
  computed: {
    newsSummaryButtonText() {
      if (this.isCreatingNewsSummary) return 'Creating...';
      return 'Create Summary Record';
    }
  },
  mounted() {
    this.checkLlmConnection();
  },
  methods: {
    async checkLlmConnection() {
      this.isCheckingLlm = true;
      try {
        await sendLlmChat([
          SYSTEM_MESSAGE,
          { role: 'user', content: 'Reply with OK.' }
        ]);
        this.noLlmConnection = false;
      } catch (error) {
        this.noLlmConnection = true;
      } finally {
        this.isCheckingLlm = false;
      }
    },
    async createNewsSummary() {
      if (this.noLlmConnection || this.isCheckingLlm || this.isCreatingNewsSummary) return;
      this.isCreatingNewsSummary = true;
      this.newsSummaryMessage = '';
      this.newsSummaryStatus = '';
      try {
        await createMarketNewsSummaryRecord();
        this.newsSummaryStatus = 'success';
        this.newsSummaryMessage = 'Summary job started.';
      } catch (error) {
        this.newsSummaryStatus = 'failed';
        this.newsSummaryMessage = 'Failed to start summary job.';
      } finally {
        this.isCreatingNewsSummary = false;
      }
    }
  }
}
</script>

<style scoped>
.admin-tools {
  max-width: 960px;
  margin: 1.5rem auto;
  padding: 1rem 1.25rem;
  border: 1px solid #e5e8ed;
  border-radius: 4px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.page-header h1 {
  margin: 0;
  color: #102a43;
}

.tools-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.tool-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 1rem;
  align-items: center;
  padding: 1rem;
  border: 1px solid #e5e8ed;
  border-radius: 4px;
  background: #f7fafc;
}

.tool-copy {
  min-width: 0;
}

.tool-copy h2 {
  margin: 0 0 0.35rem 0;
  color: #102a43;
  font-size: 1.05rem;
}

.tool-copy p,
.status-message {
  margin: 0;
  color: #52606d;
  line-height: 1.4;
}

.tool-action {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  min-width: min(100%, 26rem);
}

.status-message {
  max-width: 16rem;
  font-size: 0.9rem;
}

.status-message.success {
  color: #0f7b4d;
  font-weight: 700;
}

.status-message.failed {
  color: #d64045;
  font-weight: 700;
}

.status-message.muted {
  color: #64748b;
}

@media (max-width: 760px) {
  .tool-item {
    grid-template-columns: 1fr;
  }

  .tool-action {
    align-items: flex-start;
    justify-content: flex-start;
    flex-direction: column;
    min-width: 0;
  }
}
</style>
