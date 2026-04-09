import { z } from "zod";

export const interpretRequestSchema = z.object({
  text: z.string().trim().min(1).max(300),
  locale: z.string().trim().default("en-US"),
  recentCommands: z.array(z.string().trim().max(120)).max(5).default([]),
});

export const intentResponseSchema = z.object({
  action: z.enum([
    "open_app",
    "navigate",
    "click_text",
    "type_text",
    "scroll",
    "send_message",
    "trigger_task",
    "unknown",
  ]),
  summary: z.string(),
  appName: z.string(),
  packageName: z.string(),
  targetText: z.string(),
  inputText: z.string(),
  contact: z.string(),
  message: z.string(),
  direction: z.enum(["", "forward", "backward", "up", "down"]),
  navigationTarget: z.enum(["", "back", "home", "recents"]),
  taskName: z.string(),
  requiresConfirmation: z.boolean(),
  sensitive: z.boolean(),
  confidence: z.number().min(0).max(1),
  reason: z.string(),
});

export const openAiIntentFormat = {
  type: "json_schema",
  name: "aira_intent",
  strict: true,
  schema: {
    type: "object",
    additionalProperties: false,
    properties: {
      action: {
        type: "string",
        enum: [
          "open_app",
          "navigate",
          "click_text",
          "type_text",
          "scroll",
          "send_message",
          "trigger_task",
          "unknown",
        ],
      },
      summary: { type: "string" },
      appName: { type: "string" },
      packageName: { type: "string" },
      targetText: { type: "string" },
      inputText: { type: "string" },
      contact: { type: "string" },
      message: { type: "string" },
      direction: {
        type: "string",
        enum: ["", "forward", "backward", "up", "down"],
      },
      navigationTarget: {
        type: "string",
        enum: ["", "back", "home", "recents"],
      },
      taskName: { type: "string" },
      requiresConfirmation: { type: "boolean" },
      sensitive: { type: "boolean" },
      confidence: { type: "number" },
      reason: { type: "string" },
    },
    required: [
      "action",
      "summary",
      "appName",
      "packageName",
      "targetText",
      "inputText",
      "contact",
      "message",
      "direction",
      "navigationTarget",
      "taskName",
      "requiresConfirmation",
      "sensitive",
      "confidence",
      "reason",
    ],
  },
};

export function unknownIntent(reason) {
  return {
    action: "unknown",
    summary: "Command needs clarification",
    appName: "",
    packageName: "",
    targetText: "",
    inputText: "",
    contact: "",
    message: "",
    direction: "",
    navigationTarget: "",
    taskName: "",
    requiresConfirmation: false,
    sensitive: false,
    confidence: 0,
    reason,
  };
}

