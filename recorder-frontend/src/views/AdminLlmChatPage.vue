<template>
  <section class="llm-chat workspace-page workspace-panel">
    <header class="page-header workspace-header">
      <h1>LLM Chat</h1>
      <div class="header-actions">
        <label class="mode-toggle" title="Unchecked uses the current eager related-record context mode. Checked uses the new records-agent mode.">
          <input
            v-model="useRecordAgent"
            type="checkbox"
            :disabled="isSending"
            @change="saveAgentModePreference"
          >
          <span>Use record agent</span>
        </label>
        <label class="mode-toggle" title="Show record-agent tool usage details above LLM answers.">
          <input
            v-model="showToolUsageInfo"
            type="checkbox"
            :disabled="isSending"
            @change="saveToolUsagePreference"
          >
          <span>Show tool info</span>
        </label>
        <button
          v-if="!noConnection && !isChecking"
          type="button"
          class="text-button"
          :disabled="isSavingRecord || !visibleMessages.length"
          @click="saveChatRecord"
        >
          {{ isSavingRecord ? 'Adding...' : 'Add Chat As Record' }}
        </button>
        <button
          type="button"
          class="icon-button"
          title="Start new chat"
          aria-label="Start new chat"
          @click="refreshChatHistory"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M21 12a9 9 0 0 1-15.2 6.5" />
            <path d="M3 12a9 9 0 0 1 15.2-6.5" />
            <path d="M18 2v4h-4" />
            <path d="M6 22v-4h4" />
          </svg>
        </button>
      </div>
    </header>
    <p
      v-if="recordMessage"
      class="record-message"
      :class="{ error: recordMessageIsError }"
    >
      {{ recordMessage }}
    </p>

    <div v-if="isChecking && !visibleMessages.length" class="connection-empty">
      Checking LLM connection...
    </div>

    <div v-else-if="noConnection && !visibleMessages.length" class="connection-empty">
      No LLM connection
    </div>

    <template v-else>
      <p v-if="noConnection" class="connection-banner">
        {{ connectionBannerText }}
      </p>
      <div class="conversation" ref="conversation">
        <div
          v-for="(message, index) in visibleMessages"
          :key="index"
          class="message"
          :class="message.role"
        >
          <div class="message-meta">
            <span class="role">{{ message.role === 'user' ? 'You' : 'LLM' }}</span>
            <time v-if="message.createdAt" :datetime="message.createdAt">
              {{ formatMessageTime(message.createdAt) }}
            </time>
          </div>
          <div
            v-if="shouldShowToolUsages(message)"
            class="tool-usage"
          >
            <div
              v-for="(usage, usageIndex) in message.toolUsages"
              :key="usageIndex"
            >
              {{ usage }}
            </div>
          </div>
          <formatted-text class="content" :text="message.content" />
        </div>
      </div>

      <form class="composer" @submit.prevent="sendMessage">
        <textarea
          v-model.trim="draft"
          rows="4"
          :disabled="isSending"
          placeholder="Ask the local LLM..."
          @keydown.enter.exact.prevent="sendMessage"
        ></textarea>
        <base-button mode="primary" :disabled="isSending || !draft">
          {{ isSending ? 'Sending...' : 'Send' }}
        </base-button>
      </form>
    </template>
  </section>
</template>

<script>
import { saveLlmChatAsRecord, sendLlmChat } from '../api/LlmService.js'
import { parseJwtInfo } from '../api/UserService.js'
import FormattedText from '../components/ui/FormattedText.vue'

const SYSTEM_MESSAGE = {
  role: 'system',
  content: 'You are a concise assistant for the Recorder admin user.'
}
const CHAT_HISTORY_STORAGE_KEY = 'recorder.llmChat.messages'
const CHAT_AGENT_MODE_STORAGE_KEY = 'recorder.llmChat.recordAgentMode'
const CHAT_TOOL_USAGE_STORAGE_KEY = 'recorder.llmChat.showToolUsageInfo'

export default {
  name: 'AdminLlmChatPage',
  components: { FormattedText },
  data() {
    return {
      messages: [SYSTEM_MESSAGE],
      draft: '',
      isChecking: true,
      isSending: false,
      isSavingRecord: false,
      recordMessage: '',
      recordMessageIsError: false,
      noConnection: false,
      useRecordAgent: false,
      showToolUsageInfo: false
    }
  },
  computed: {
    visibleMessages() {
      return this.messages.filter((message) => message.role !== 'system');
    },
    connectionBannerText() {
      return this.recordMessage || 'LLM connection failed. Your chat history is kept; send again after the model is reachable.';
    }
  },
  mounted() {
    this.loadAgentModePreference();
    this.loadToolUsagePreference();
    this.loadChatHistory();
    this.checkConnection();
  },
  methods: {
    getChatMode() {
      return this.useRecordAgent ? 'RECORD_AGENT' : 'RELATED_CONTEXT';
    },
    loadAgentModePreference() {
      this.useRecordAgent = localStorage.getItem(CHAT_AGENT_MODE_STORAGE_KEY) === 'true';
    },
    saveAgentModePreference() {
      localStorage.setItem(CHAT_AGENT_MODE_STORAGE_KEY, this.useRecordAgent ? 'true' : 'false');
    },
    loadToolUsagePreference() {
      this.showToolUsageInfo = localStorage.getItem(CHAT_TOOL_USAGE_STORAGE_KEY) === 'true';
    },
    saveToolUsagePreference() {
      localStorage.setItem(CHAT_TOOL_USAGE_STORAGE_KEY, this.showToolUsageInfo ? 'true' : 'false');
    },
    getStoredTokenInfo() {
      return parseJwtInfo(localStorage.getItem('token'));
    },
    clearStoredChatHistory() {
      localStorage.removeItem(CHAT_HISTORY_STORAGE_KEY);
      this.messages = [SYSTEM_MESSAGE];
    },
    loadChatHistory() {
      const tokenInfo = this.getStoredTokenInfo();
      if (tokenInfo.expired) {
        this.clearStoredChatHistory();
        return;
      }
      try {
        const parsed = JSON.parse(localStorage.getItem(CHAT_HISTORY_STORAGE_KEY) || '[]');
        if (!Array.isArray(parsed)) {
          this.messages = [SYSTEM_MESSAGE];
          return;
        }
        const storedMessages = parsed
          .map(this.normalizeStoredMessage)
          .filter(Boolean);
        this.messages = [SYSTEM_MESSAGE, ...storedMessages];
        this.$nextTick(this.scrollToBottom);
      } catch (error) {
        this.clearStoredChatHistory();
      }
    },
    saveChatHistory() {
      const tokenInfo = this.getStoredTokenInfo();
      if (tokenInfo.expired) {
        this.clearStoredChatHistory();
        return;
      }
      localStorage.setItem(
        CHAT_HISTORY_STORAGE_KEY,
        JSON.stringify(this.visibleMessages)
      );
    },
    refreshChatHistory() {
      this.clearStoredChatHistory();
      this.draft = '';
      this.recordMessage = '';
      this.recordMessageIsError = false;
      this.$nextTick(this.scrollToBottom);
    },
    clearHistoryIfAuthError(error) {
      const status = error?.response?.status;
      if (status === 401 || status === 403) {
        this.clearStoredChatHistory();
        return true;
      }
      return false;
    },
    async checkConnection() {
      try {
        await sendLlmChat([
          SYSTEM_MESSAGE,
          { role: 'user', content: 'Reply with OK.' }
        ]);
        this.noConnection = false;
      } catch (error) {
        if (this.clearHistoryIfAuthError(error)) {
          this.noConnection = true;
          return;
        }
        this.noConnection = true;
      } finally {
        this.isChecking = false;
      }
    },
    normalizeStoredMessage(message) {
      if (!['user', 'assistant'].includes(message?.role) || typeof message.content !== 'string') {
        return null;
      }
      if (message.role === 'assistant' && /<\|?\/?tool_calls?\|?>/i.test(message.content)) {
        return null;
      }
      return {
        role: message.role,
        content: message.content,
        createdAt: this.isValidDateString(message.createdAt) ? message.createdAt : null,
        toolUsages: Array.isArray(message.toolUsages)
          ? message.toolUsages.filter((usage) => typeof usage === 'string' && usage.trim())
          : []
      };
    },
    createChatMessage(role, content, toolUsages = []) {
      return {
        role,
        content,
        createdAt: new Date().toISOString(),
        toolUsages: Array.isArray(toolUsages) ? toolUsages : []
      };
    },
    shouldShowToolUsages(message) {
      return this.showToolUsageInfo
        && message?.role === 'assistant'
        && Array.isArray(message.toolUsages)
        && message.toolUsages.length > 0;
    },
    isValidDateString(value) {
      return typeof value === 'string' && !Number.isNaN(new Date(value).getTime());
    },
    formatMessageTime(value) {
      if (!this.isValidDateString(value)) return '';
      return new Date(value).toLocaleString([], {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    },
    async sendMessage() {
      if (!this.draft || this.isSending) return;
      const userMessage = this.createChatMessage('user', this.draft);
      this.messages.push(userMessage);
      this.saveChatHistory();
      this.draft = '';
      this.isSending = true;
      try {
        const response = await sendLlmChat(this.messages, {
          includeRelatedRecords: !this.useRecordAgent,
          chatMode: this.getChatMode()
        });
        if (!response?.reply) {
          throw new Error('No LLM reply');
        }
        this.messages.push(this.createChatMessage('assistant', response.reply, response.toolUsages));
        this.noConnection = false;
        this.recordMessage = '';
        this.recordMessageIsError = false;
        this.saveChatHistory();
        this.$nextTick(this.scrollToBottom);
      } catch (error) {
        if (this.clearHistoryIfAuthError(error)) {
          this.noConnection = true;
          return;
        }
        this.saveChatHistory();
        this.noConnection = true;
        this.recordMessage = error?.code === 'ECONNABORTED'
          ? 'LLM response timed out. Your message was kept; agent mode can take longer when it searches records.'
          : 'LLM connection failed. Your message was kept.';
        this.recordMessageIsError = true;
      } finally {
        this.isSending = false;
      }
    },
    async saveChatRecord() {
      if (this.isSavingRecord || !this.visibleMessages.length) return;
      this.isSavingRecord = true;
      this.recordMessage = '';
      this.recordMessageIsError = false;
      try {
        const response = await saveLlmChatAsRecord(this.visibleMessages, false);
        if (response?.status !== 'started' && !response?.recordId) {
          throw new Error('Record job was not started');
        }
        this.recordMessage = response?.message || 'Chat record creation started.';
        this.recordMessageIsError = false;
      } catch (error) {
        this.clearHistoryIfAuthError(error);
        this.recordMessage = 'Failed to add chat as record.';
        this.recordMessageIsError = true;
      } finally {
        this.isSavingRecord = false;
      }
    },
    scrollToBottom() {
      const el = this.$refs.conversation;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    }
  }
}
</script>

<style scoped>
.llm-chat {
  max-width: 960px;
  min-height: calc(100vh - 6rem);
  max-height: calc(100vh - 3rem);
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.page-header h1 {
  margin: 0;
  color: #0f4c81;
  font-size: 1.4rem;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.mode-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-height: 2.25rem;
  color: #334e68;
  font-size: 0.78rem;
  font-weight: 700;
  white-space: nowrap;
}

.mode-toggle input {
  width: 1rem;
  height: 1rem;
  accent-color: #2f80ed;
}

.icon-button {
  display: inline-grid;
  place-items: center;
  width: 2.25rem;
  height: 2.25rem;
  border: 1px solid #cfd7e2;
  border-radius: 4px;
  background: #f7fafc;
  color: #0f4c81;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
}

.icon-button svg {
  width: 1.15rem;
  height: 1.15rem;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.icon-button:hover,
.icon-button:focus-visible {
  border-color: #2f80ed;
  background: #eef6ff;
  outline: none;
}

.text-button {
  min-height: 2.25rem;
  padding: 0.42rem 0.65rem;
  border: 1px solid #cfd7e2;
  border-radius: 4px;
  background: #f7fafc;
  color: #0f4c81;
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
}

.text-button:hover,
.text-button:focus-visible {
  border-color: #2f80ed;
  background: #eef6ff;
  outline: none;
}

.text-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.record-message {
  margin: -0.25rem 0 0;
  color: #0f7b4d;
  font-size: 0.8rem;
  font-weight: 700;
}

.record-message.error {
  color: #d64045;
}

.connection-empty {
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  background: #f7fafc;
  color: #52606d;
  padding: 0.85rem;
  font-size: 0.8rem;
  flex: 1;
}

.connection-banner {
  margin: 0;
  padding: 0.55rem 0.7rem;
  border: 1px solid #f0c7c1;
  border-radius: 4px;
  background: #fff7f5;
  color: #b42318;
  font-size: 0.78rem;
  font-weight: 700;
}

.conversation {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  background: #f7fafc;
  padding: 0.6rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.message {
  max-width: min(44rem, 88%);
  padding: 0.5rem 0.6rem;
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  background: #fff;
  overflow: visible;
}

.message.user {
  align-self: flex-end;
  background: #e5f3ff;
}

.message.assistant {
  align-self: flex-start;
}

.message-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.7rem;
  margin-bottom: 0.22rem;
}

.role,
.message-meta time {
  font-size: 0.72rem;
  line-height: 1.15;
}

.role {
  font-weight: 700;
  color: #52606d;
}

.message-meta time {
  color: #7b8794;
  white-space: nowrap;
}

.content {
  font-size: 0.84rem;
  overflow: visible;
  line-height: 1.38;
}

.tool-usage {
  margin: 0 0 0.45rem;
  padding: 0.35rem 0.45rem;
  border: 1px solid #cfe0f5;
  border-radius: 4px;
  background: #f3f8ff;
  color: #334e68;
  font-size: 0.74rem;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.composer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.75rem;
  align-items: end;
}

textarea {
  width: 100%;
  resize: none;
  height: 7rem;
  border: 1px solid #cfd7e2;
  border-radius: 4px;
  padding: 0.6rem;
  font: inherit;
  font-size: 0.84rem;
}

textarea:focus {
  outline: none;
  border-color: #2f80ed;
  box-shadow: 0 0 0 3px rgba(47, 128, 237, 0.14);
}

@media (max-width: 640px) {
  .llm-chat {
    min-height: calc(100vh - 5rem);
    max-height: none;
    margin: 0.75rem;
  }

  .composer {
    grid-template-columns: 1fr;
  }

  textarea {
    height: 6rem;
  }
}
</style>
