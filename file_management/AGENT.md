# File Management AI customer support

The customer-support agent is part of the `file_management` Spring Boot module.
It exposes three Spring AI tools: `topKSimilarity`, `summarizeContent`, and
`fileExtendAutomation`.

## Request flow

```text
POST /api/agent/chat (sessionId, userInput, userId)
  -> validate request
  -> isolate memory by userId:sessionId
  -> model decides whether repository knowledge is required
  -> selected tool reads userId from ToolContext
  -> ChunkService.queryTopKSimilarity
  -> hybrid vector/keyword retrieval + permission check
  -> tool removes every invisible hit and empty body
  -> summarizeContent optionally generates a focused summary from readable chunks
  -> model synthesizes only readable content returned by the tools
  -> DataVO<String> response
```

`userId` follows the repository's current local-development model. When
authentication is added, the controller should receive the authenticated user
identity from middleware instead of a request parameter. The model itself can
never supply or change `userId` or `topK`.

## Run

Set `OPENAI_API_KEY` in the root `.env`, then start the normal backend services:

```bash
./script/start-all.sh
```

The agent uses the existing `file_management` port, `8087`:

```bash
curl -X POST "http://localhost:8087/api/agent/chat" \
  --data-urlencode "sessionId=local-test" \
  --data-urlencode "userInput=如何申请一个文件的读取权限？" \
  --data-urlencode "userId=1"
```

Configuration:

- `AGENT_TOP_K` defaults to `6` and is limited to `1..50`.
- `AGENT_MAX_ITERATIONS` defaults to `4`.
- `AGENT_MEMORY_MAX_MESSAGES` defaults to `20`.
