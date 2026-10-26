// Buddy AI for Pro users. Called by the apps with the signed-in user's Supabase token.
// Keeps the Anthropic API key server-side, enforces monthly limits and caches reports.
//
// Deploy: supabase functions deploy coach
// Secrets: supabase secrets set ANTHROPIC_API_KEY=...

import Anthropic from "npm:@anthropic-ai/sdk@^0.136.0";
import { createClient } from "npm:@supabase/supabase-js@2";
import { BUDDY_SYSTEM, ROUTINE_TASK, SUMMARY_TASK, WEEKLY_TASK } from "./prompts.ts";

type Kind = "chat" | "summary" | "weekly" | "routine";
type Plan = "free" | "pro" | "lifetime";

// Requests per calendar month (UTC).
const LIMITS: Record<Plan, Record<Kind, number>> = {
  free: { chat: 5, summary: 0, weekly: 0, routine: 0 },
  pro: { chat: 300, summary: 60, weekly: 6, routine: 10 },
  lifetime: { chat: 50, summary: 60, weekly: 6, routine: 5 },
};

// Haiku for quick notes and chat; Sonnet where reasoning over weeks of data pays off.
const MODELS: Record<Kind, { model: string; effort: "low" | "medium"; maxTokens: number }> = {
  chat: { model: "claude-haiku-5-5", effort: "low", maxTokens: 4000 },
  summary: { model: "claude-haiku-5-5", effort: "low", maxTokens: 2000 },
  weekly: { model: "claude-sonnet-5-5", effort: "medium", maxTokens: 6000 },
  routine: { model: "claude-sonnet-5-5", effort: "medium", maxTokens: 12000 },
};

const MAX_CONTEXT_BYTES = 60_000;
const MAX_MESSAGE_CHARS = 2_000;
const HISTORY_MESSAGES = 20;

const anthropic = new Anthropic();
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

const PROPOSE_ROUTINE: Anthropic.Tool = {
  name: "propose_routine",
  description: "Propose a routine the app shows to the person; they decide whether to save it as templates.",
  input_schema: {
    type: "object",
    additionalProperties: false,
    required: ["name", "days"],
    properties: {
      name: { type: "string", description: "Short name, in the person's language" },
      days: {
        type: "array",
        minItems: 1,
        maxItems: 7,
        items: {
          type: "object",
          additionalProperties: false,
          required: ["name", "exercises"],
          properties: {
            name: { type: "string" },
            exercises: {
              type: "array",
              minItems: 1,
              maxItems: 12,
              items: {
                type: "object",
                additionalProperties: false,
                required: ["exerciseId", "sets", "repsLow", "repsHigh", "restSeconds"],
                properties: {
                  exerciseId: { type: "string", description: "An id from context.library" },
                  sets: { type: "integer", minimum: 1, maximum: 10 },
                  repsLow: { type: "integer", minimum: 1, maximum: 50 },
                  repsHigh: { type: "integer", minimum: 1, maximum: 50 },
                  restSeconds: { type: "integer", minimum: 0, maximum: 600 },
                },
              },
            },
          },
        },
      },
    },
  },
};

interface Routine {
  name: string;
  days: { name: string; exercises: { exerciseId: string; sets: number; repsLow: number; repsHigh: number; restSeconds: number }[] }[];
}

/** The tool input, if it is a complete routine that only uses exercises from the library. */
function validRoutine(input: unknown, library: Set<string>): Routine | null {
  const r = input as Routine;
  const int = (n: unknown, lo: number, hi: number) => Number.isInteger(n) && (n as number) >= lo && (n as number) <= hi;
  if (typeof r?.name !== "string" || !Array.isArray(r.days) || r.days.length < 1 || r.days.length > 7) return null;
  for (const day of r.days) {
    if (typeof day?.name !== "string" || !Array.isArray(day.exercises) || day.exercises.length < 1) return null;
    for (const e of day.exercises) {
      if (!library.has(e?.exerciseId) || !int(e.sets, 1, 10) || !int(e.repsLow, 1, 50) || !int(e.repsHigh, e.repsLow, 50) || !int(e.restSeconds, 0, 600)) {
        return null;
      }
    }
  }
  return r;
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

const month = () => new Date().toISOString().slice(0, 7);

function systemBlocks(kind: Kind, context: unknown, locale: string, tone: string): Anthropic.TextBlockParam[] {
  const task = { chat: null, summary: SUMMARY_TASK, weekly: WEEKLY_TASK, routine: ROUTINE_TASK }[kind];
  return [
    { type: "text", text: BUDDY_SYSTEM, cache_control: { type: "ephemeral" } },
    // Per person; cached too, so the follow-ups of a chat re-read it cheaply.
    { type: "text", text: JSON.stringify({ locale, tone, context }), cache_control: { type: "ephemeral" } },
    ...(task ? [{ type: "text" as const, text: task }] : []),
  ];
}

// Sonnet 5.5 can route a declined request to another model server-side.
function fallbackParams(model: string) {
  return model === "claude-sonnet-5-5" ? { betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" } : {};
}

function text(message: { content: { type: string; text?: string }[] }) {
  return message.content.filter((b) => b.type === "text").map((b) => b.text).join("").trim();
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  const { data: auth } = await admin.auth.getUser(token ?? "");
  const user = auth?.user;
  if (!user) return json({ error: "auth" }, 401);

  let body: { kind?: Kind; plan?: Plan; locale?: string; tone?: string; context?: unknown; message?: string; ref?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }
  const kind = body.kind;
  if (!kind || !(kind in MODELS)) return json({ error: "bad_request" }, 400);
  // Pro is bought in the app stores; until receipts are verified server-side the app's claim
  // is trusted, and the monthly limits cap what any one account can spend.
  const plan: Plan = body.plan === "pro" || body.plan === "lifetime" ? body.plan : "free";
  const locale = body.locale === "en" ? "en" : "vi";
  const tone = ["gentle", "balanced", "strict"].includes(body.tone ?? "") ? body.tone! : "balanced";
  const contextJson = JSON.stringify(body.context ?? {});
  if (contextJson.length > MAX_CONTEXT_BYTES) return json({ error: "too_large" }, 413);
  const message = (body.message ?? "").trim().slice(0, MAX_MESSAGE_CHARS);
  if ((kind === "chat" || kind === "routine") && !message) return json({ error: "bad_request" }, 400);
  const ref = (body.ref ?? "").slice(0, 100);

  // A report made before is free to open again.
  if (kind !== "chat" && ref) {
    const { data: cached } = await admin.from("coach_reports").select("content")
      .eq("user_id", user.id).eq("kind", kind).eq("ref", ref).maybeSingle();
    if (cached) return json({ ...cached.content, cached: true });
  }

  const { data: allowed, error: quotaError } = await admin.rpc("coach_consume", {
    p_user: user.id, p_month: month(), p_kind: kind, p_limit: LIMITS[plan][kind],
  });
  if (quotaError) return json({ error: "server" }, 500);
  if (!allowed) return json({ error: "quota", limit: LIMITS[plan][kind] }, 429);

  const { model, effort, maxTokens } = MODELS[kind];
  const system = systemBlocks(kind, body.context ?? {}, locale, tone);
  const recordTokens = (usage: { input_tokens: number; output_tokens: number; cache_read_input_tokens?: number | null; cache_creation_input_tokens?: number | null }) =>
    admin.rpc("coach_add_tokens", {
      p_user: user.id, p_month: month(), p_kind: kind,
      p_input: usage.input_tokens + (usage.cache_read_input_tokens ?? 0) + (usage.cache_creation_input_tokens ?? 0),
      p_output: usage.output_tokens,
    });

  if (kind === "chat") return chat(user.id, system, message, recordTokens);

  try {
    const userText = kind === "routine" ? message : "Go.";
    const library = new Set<string>(
      ((body.context as { library?: { id: string }[] })?.library ?? []).map((e) => e.id),
    );
    const response = await anthropic.beta.messages.create({
      model,
      max_tokens: maxTokens,
      output_config: { effort },
      system,
      messages: [{ role: "user", content: userText }],
      ...(kind === "routine" ? { tools: [PROPOSE_ROUTINE], tool_choice: { type: "auto" as const } } : {}),
      ...fallbackParams(model),
    } as Anthropic.Beta.MessageCreateParamsNonStreaming);
    await recordTokens(response.usage);
    if (response.stop_reason === "refusal") return json({ error: "refusal" }, 422);
    if (response.stop_reason === "max_tokens") return json({ error: "server" }, 502);
    const content: Record<string, unknown> = { text: text(response) };
    if (kind === "routine") {
      const call = response.content.find((b) => b.type === "tool_use" && b.name === "propose_routine") as { input?: unknown } | undefined;
      const routine = validRoutine(call?.input, library);
      if (!routine) return json({ error: "no_routine", text: content.text }, 502);
      content.routine = routine;
    }
    if (ref) {
      await admin.from("coach_reports").upsert({ user_id: user.id, kind, ref, content }, { onConflict: "user_id,kind,ref" });
    }
    return json(content);
  } catch (e) {
    console.error(kind, e instanceof Anthropic.APIError ? `${e.status} ${e.message}` : e);
    return json({ error: e instanceof Anthropic.RateLimitError ? "busy" : "server" }, 502);
  }
});

/** Streams the answer as server-sent events: {"type":"text","text":…} deltas, then {"type":"done"} or {"type":"error"}. */
async function chat(
  userId: string,
  system: Anthropic.TextBlockParam[],
  message: string,
  recordTokens: (usage: Anthropic.Usage) => PromiseLike<unknown>,
): Promise<Response> {
  const { data: rows } = await admin.from("coach_messages").select("role, content")
    .eq("user_id", userId).order("created_at", { ascending: false }).limit(HISTORY_MESSAGES);
  const history = (rows ?? []).reverse() as { role: "user" | "assistant"; content: string }[];
  // The API needs turns to alternate and start with the person.
  const messages: Anthropic.MessageParam[] = [];
  for (const row of history) {
    if (messages.length === 0 && row.role !== "user") continue;
    const last = messages.at(-1);
    if (last && last.role === row.role) last.content = `${last.content}\n\n${row.content}`;
    else messages.push({ role: row.role, content: row.content });
  }
  if (messages.at(-1)?.role === "user") messages.pop();
  messages.push({ role: "user", content: message });

  const encoder = new TextEncoder();
  const { model, effort, maxTokens } = MODELS.chat;
  const body = new ReadableStream({
    async start(controller) {
      const send = (event: unknown) => controller.enqueue(encoder.encode(`data: ${JSON.stringify(event)}\n\n`));
      try {
        const stream = anthropic.messages.stream({ model, max_tokens: maxTokens, output_config: { effort }, system, messages });
        stream.on("text", (delta) => send({ type: "text", text: delta }));
        const final = await stream.finalMessage();
        await recordTokens(final.usage);
        if (final.stop_reason === "refusal") {
          send({ type: "error", code: "refusal" });
        } else {
          await admin.from("coach_messages").insert([
            { user_id: userId, role: "user", content: message },
            { user_id: userId, role: "assistant", content: text(final) },
          ]);
          send({ type: "done" });
        }
      } catch (e) {
        console.error("chat", e instanceof Anthropic.APIError ? `${e.status} ${e.message}` : e);
        send({ type: "error", code: e instanceof Anthropic.RateLimitError ? "busy" : "server" });
      }
      controller.close();
    },
  });
  return new Response(body, { headers: { "content-type": "text/event-stream", "cache-control": "no-cache" } });
}
