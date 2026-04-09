import "dotenv/config";
import cors from "cors";
import express from "express";
import helmet from "helmet";
import {
  intentResponseSchema,
  interpretRequestSchema,
  openAiIntentFormat,
  unknownIntent,
} from "./intentSchema.js";

const app = express();
const port = Number(process.env.PORT || 3000);
const openAiApiKey = process.env.OPENAI_API_KEY;
const model = process.env.OPENAI_MODEL || "gpt-4o-mini";

app.use(helmet());
app.use(cors());
app.use(express.json({ limit: "1mb" }));

app.get("/health", (_request, response) => {
  response.json({
    ok: true,
    service: "aira-intent-backend",
    model,
    openAiConfigured: Boolean(openAiApiKey),
  });
});

app.post("/interpret", async (request, response) => {
  if (!openAiApiKey) {
    response.status(500).json({
      error: "OPENAI_API_KEY is missing.",
      intent: unknownIntent("The backend is missing its OpenAI credentials."),
    });
    return;
  }

  const parsedBody = interpretRequestSchema.safeParse(request.body);
  if (!parsedBody.success) {
    response.status(400).json({
      error: "Invalid request payload.",
      details: parsedBody.error.flatten(),
    });
    return;
  }

  try {
    const rawIntent = await interpretWithOpenAi(parsedBody.data);
    const safeIntent = enforceLocalSafety(rawIntent);
    response.json({ intent: safeIntent });
  } catch (error) {
    response.status(502).json({
      error: "Interpretation failed.",
      details: error instanceof Error ? error.message : "Unknown failure",
      intent: unknownIntent("Aira could not interpret the command right now."),
    });
  }
});

app.listen(port, () => {
  console.log(`Aira backend listening on http://localhost:${port}`);
});

async function interpretWithOpenAi(payload) {
  const requestBody = {
    model,
    input: [
      {
        role: "system",
        content: [
          {
            type: "input_text",
            text: systemPrompt,
          },
        ],
      },
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: buildUserPrompt(payload),
          },
        ],
      },
    ],
    text: {
      format: openAiIntentFormat,
    },
  };

  const openAiResponse = await fetch("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${openAiApiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(requestBody),
  });

  const responseJson = await openAiResponse.json();
  if (!openAiResponse.ok) {
    throw new Error(responseJson?.error?.message || "OpenAI request failed.");
  }

  return extractIntent(responseJson);
}

function buildUserPrompt(payload) {
  const context = {
    locale: payload.locale,
    recentCommands: payload.recentCommands,
    userCommand: payload.text,
  };
  return JSON.stringify(context, null, 2);
}

function extractIntent(responseJson) {
  const message = responseJson.output?.find((item) => item.type === "message");
  if (!message) {
    throw new Error("OpenAI response did not contain an assistant message.");
  }

  const refusal = message.content?.find((item) => item.type === "refusal");
  if (refusal?.refusal) {
    return unknownIntent(refusal.refusal);
  }

  const outputText = message.content?.find((item) => item.type === "output_text");
  if (!outputText?.text) {
    throw new Error("Structured output text was missing.");
  }

  const parsed = JSON.parse(outputText.text);
  return intentResponseSchema.parse(parsed);
}

function enforceLocalSafety(intent) {
  const next = { ...intent };
  const action = next.action;

  if (action === "send_message" || action === "type_text" || action === "click_text") {
    next.requiresConfirmation = true;
    next.sensitive = true;
  }

  if (action === "navigate" && !["back", "home", "recents"].includes(next.navigationTarget)) {
    return unknownIntent("Navigation target was incomplete.");
  }

  if (action === "scroll" && !["forward", "backward", "up", "down"].includes(next.direction)) {
    return unknownIntent("Scroll direction was incomplete.");
  }

  if (action === "open_app" && !next.appName && !next.packageName) {
    return unknownIntent("The app name was not clear enough to execute safely.");
  }

  if (action === "send_message" && !next.contact) {
    return unknownIntent("A contact is required before messaging automation can continue.");
  }

  if (next.confidence < 0.55 && action !== "unknown") {
    next.requiresConfirmation = true;
    next.reason = next.reason || "Low-confidence intent; confirmation requested.";
  }

  if (!next.summary) {
    next.summary = action === "unknown" ? "Command needs clarification" : action.replaceAll("_", " ");
  }

  return next;
}

const systemPrompt = `
You are the intent engine for Aira, an Android automation assistant.

Return a single JSON object that exactly matches the provided schema.

Rules:
- Only use these actions: open_app, navigate, click_text, type_text, scroll, send_message, trigger_task, unknown.
- Prefer unknown instead of guessing when user intent is ambiguous.
- For open_app, fill appName and packageName when obvious. If package name is unknown, leave it empty.
- For navigate, only use back, home, or recents.
- For click_text, set targetText to the visible label the accessibility service should find.
- For type_text, set inputText to the content that should be typed, and use targetText as an optional field hint when helpful.
- For scroll, use forward, backward, up, or down.
- For send_message, fill appName, contact, and message if present. If the message body is missing, keep message empty and explain that in reason.
- For trigger_task, only use it when the request explicitly sounds like a named automation routine or Tasker-style workflow.
- Set requiresConfirmation to true for anything that sends, types, taps, or could have side effects.
- Set sensitive to true for actions that can modify state or communicate on behalf of the user.
- Confidence should be between 0 and 1.
- Keep missing string fields as empty strings.
`.trim();
