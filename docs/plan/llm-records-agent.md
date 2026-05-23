# Records-Only LLM Chat Agent Plan

## Summary

Create `docs/plan/llm-records-agent.md` with this checklist plan, then implement a testable records-only agent path beside the existing eager Qdrant enrichment path. The new implementation will use reusable agent/tool interfaces so future stock and option tools can be added without rewriting the agent loop. During implementation, check off each completed major item in this plan file.

## Implementation Checklist

- [x] Create this plan file at `docs/plan/llm-records-agent.md`.
- [x] Add a reusable backend agent service, for example `LlmAgentService`, responsible for the generic tool loop.
- [x] Add universal tool interfaces, for example:
  - [x] `LlmAgentTool`: tool name, description, argument schema/instructions, execute method.
  - [x] `LlmAgentToolRegistry`: registers tools by name and rejects unknown tools.
  - [x] `LlmAgentToolCall`: parsed tool name and arguments.
  - [x] `LlmAgentToolResult`: success/error result rendered back to the model.
- [x] Implement the records tool as the first concrete tool, for example `SearchRecordsAgentTool`.
- [x] Keep the old eager related-record code in a separate existing path: `LlmRecordService.enrichChatWithRelatedChunks`.
- [x] Keep the new records-agent code in separate classes so the old path can be disabled or deleted cleanly later.
- [x] Update `LlmController.chat` to route by request mode:
  - [x] old mode: eager related-record enrichment, then `MarketPulseApiService.chatWithLlm`.
  - [x] new mode: generic `LlmAgentService` with registered tools.
- [x] Keep admin-only access unchanged for `/api/llm/chat`.
- [x] Use the existing Market Pulse `/llm/chat` plain-text endpoint; do not add native OpenAI tool calls in v1.
- [x] Add frontend checkbox state in `AdminLlmChatPage.vue` to switch old/new mode.
- [x] Update `LlmService.sendLlmChat` to send the selected mode.
- [x] Keep label generation and chat-to-record routing unchanged for the agent rollout. Later update: chat-to-record now saves summary-only content instead of appending transcript text.
- [x] After each major implementation item is finished, update this plan file by checking its checkbox.

## Agent And Tool Behavior

- [x] Add a request field such as `chatMode`, with values:
  - `RELATED_CONTEXT`: old eager Qdrant enrichment.
  - `RECORD_AGENT`: new generic agent flow with records tool enabled.
- [x] Default to `RELATED_CONTEXT` for compatibility unless the UI checkbox selects agent mode.
- [x] In `RECORD_AGENT`, the backend builds a reusable system prompt from registered tool metadata.
- [x] The model may return either a final answer or a JSON tool request.
- [x] Tool request format:
  ```json
  {
    "tool": "search_records",
    "arguments": {
      "query": "text to search for",
      "limit": 5
    }
  }
  ```
- [x] Final answer format:
  ```json
  {
    "final": "answer text"
  }
  ```
- [x] If the model returns normal prose instead of JSON, treat it as the final answer.
- [x] Support only one concrete v1 tool: `search_records`.
- [x] `search_records` uses existing Qdrant search through `QdrantEmbeddingService`.
- [x] Reuse current related-record safety rules: DB visibility recheck, recent-record preference, old-record filtering unless historical query or high score.
- [x] Return bounded tool results to the model: up to 5 chunks, max 1200 chars per chunk, max about 7000 chars total.
- [x] Include source title, record id, created date, modified date, similarity score, and `possibly outdated` marker in tool output.
- [x] Run at most 3 tool iterations per user message.
- [x] If a tool fails, return a controlled tool-error result to the model and let it answer without that context.
- [x] If the model requests an unknown tool, return a controlled tool-error result and do not execute anything.

## Public API / UI Changes

- [x] Keep endpoint: `POST /api/llm/chat`.
- [x] Keep response shape initially: `{ "reply": "..." }`.
- [x] Extend chat request with `chatMode`; keep `includeRelatedRecords` temporarily for backward compatibility.
- [x] Add an admin chat checkbox labeled to distinguish the new records-agent mode from the current related-record context mode.
- [x] Persist the checkbox setting in local storage so repeated testing keeps the selected mode.
- [x] No stock or option tools in this plan, but the new tool interfaces must be general enough to add them later as separate `LlmAgentTool` implementations.

## Test Plan

- [x] Unit test: `RELATED_CONTEXT` uses the existing eager enrichment path.
- [x] Unit test: `RECORD_AGENT` direct-answer model output returns `{ reply }` without Qdrant search.
- [x] Unit test: `RECORD_AGENT` JSON `search_records` request triggers the registered tool and feeds bounded results into the next LLM call.
- [x] Unit test: tool registry rejects unknown tools without executing anything.
- [x] Unit test: invalid JSON/prose response is treated as final text instead of failing.
- [x] Unit test: max tool iteration limit stops looping and asks the model for a final answer.
- [x] Unit test: Qdrant/tool failures do not fail the whole chat when the LLM can still answer.
- [x] Controller test: non-admin user still receives forbidden response.
- [x] Frontend check: checkbox changes the request `chatMode` and does not affect saved chat messages.

## Assumptions

- [x] V1 prioritizes local-model compatibility over native OpenAI tool-call support.
- [x] The old eager Qdrant approach remains available until the new agent path is validated.
- [x] Agent/tool abstractions should be reusable, but only records search is implemented now.
- [x] Existing Qdrant indexing and visibility behavior remains unchanged.
- [x] Stock history and option history tools will be planned and implemented separately after the records-only agent works.
