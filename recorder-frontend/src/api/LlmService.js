import http from './http'

function toApiMessages(messages) {
  return messages.map(({ role, content }) => ({ role, content }));
}

export async function sendLlmChat(messages, { includeRelatedRecords = false, chatMode = null } = {}) {
  const timeout = chatMode === 'RECORD_AGENT' ? 3 * 60 * 1000 : 60 * 1000;
  const response = await http.post('/llm/chat', {
    messages: toApiMessages(messages),
    temperature: 0.2,
    max_tokens: 5000,
    includeRelatedRecords,
    chatMode
  }, {
    timeout
  });
  return response.data;
}

export async function generateRecordLabels({ title = '', content = '', maxLabels = 8 } = {}) {
  const response = await http.post('/llm/record-labels', {
    title,
    content,
    maxLabels
  }, {
    timeout: 60 * 1000
  });
  return response.data;
}

export async function saveLlmChatAsRecord(messages, isPublic = false) {
  const response = await http.post('/llm/chat-record', {
    messages: toApiMessages(messages),
    public: isPublic
  }, {
    timeout: 3 * 60 * 1000
  });
  return response.data;
}
