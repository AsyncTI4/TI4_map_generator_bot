#!/usr/bin/env node
// Live dashboard for the AI A/B queue (scripts/ai-ab-queue.mjs). Read-only: it never approves, cancels or starts jobs.
//
//   node scripts/ai-ab-dashboard.mjs [--port 4377]      then open http://127.0.0.1:4377
//
// It reads <repo>/.git/ai-ab-queue (or AI_AB_QUEUE_DIR): jobs/*.json for status and order, logs/<id>.log for the
// build phase, "Game N/M finished" lines and the live log tail, and runner.lock for the runner.
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";

const HOUR = 3_600_000;
const FINISHED = new Set(["done", "failed", "cancelled", "interrupted"]);
const port = Number(process.argv[process.argv.indexOf("--port") + 1]) || Number(process.env.PORT) || 4377;

function queueDir() {
  if (process.env.AI_AB_QUEUE_DIR) return path.resolve(process.env.AI_AB_QUEUE_DIR);
  const common = execFileSync("git", ["rev-parse", "--path-format=absolute", "--git-common-dir"], {
    encoding: "utf8",
  }).trim();
  return path.join(common, "ai-ab-queue");
}

const QUEUE = queueDir();

function readJobs() {
  const dir = path.join(QUEUE, "jobs");
  if (!fs.existsSync(dir)) return [];
  const jobs = [];
  for (const name of fs.readdirSync(dir).filter(n => n.endsWith(".json"))) {
    try {
      jobs.push(JSON.parse(fs.readFileSync(path.join(dir, name), "utf8")));
    } catch {}
  }
  return jobs.sort((a, b) => a.submittedAt.localeCompare(b.submittedAt));
}

function isAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return error.code === "EPERM";
  }
}

function runner() {
  try {
    const holder = JSON.parse(fs.readFileSync(path.join(QUEUE, "runner.lock"), "utf8"));
    return isAlive(holder.pid) ? holder : null;
  } catch {
    return null;
  }
}

const logPath = id => path.join(QUEUE, "logs", `${path.basename(id)}.log`);
const argValue = (job, key) => job.args?.find(a => a.startsWith(`-D${key}=`))?.split("=").slice(1).join("=");

const GAME_LINE = /^Game (\d+)\/(\d+) finished: (\S+) in round (\d+), victory points \{([^}]*)\}, wall time (\d+)s(?:, game thread CPU (\d+)s)?/;
const GAME_ROUND = /(?<![A-Za-z0-9])(aiab\d+[ab]|aiseed\d+)(?:-round-(\d+)| r(\d+)(?![0-9]))/;
const AGENDA_HEADER = /^AGENDA TRACE (\S+) r(\d+) #(\d+)/;
const TURNS_SUMMARY = /^TURNS (\S+) (\w*): (\d+), average game time \d+s, average AI thinking ([\d.]+)s/;
const NOT_SEATS = new Set(["", "strategy", "statusscoring", "statushomework", "agenda"]);

// Incremental scan per log: only bytes appended since the last look are read.
const scans = new Map();

function scanLog(id) {
  const file = logPath(id);
  let stat;
  try {
    stat = fs.statSync(file);
  } catch {
    return null;
  }
  let scan = scans.get(id);
  if (!scan || stat.size < scan.offset) {
    scan = { offset: 0, carry: "", games: [], live: new Map(), phase: "starting", playingSince: null, build: null, errors: 0, firstLine: null };
    scans.set(id, scan);
  }
  if (stat.size > scan.offset) {
    const fd = fs.openSync(file, "r");
    const chunk = 8 * 1024 * 1024;
    while (scan.offset < stat.size) {
      const length = Math.min(chunk, stat.size - scan.offset);
      const buffer = Buffer.alloc(length);
      fs.readSync(fd, buffer, 0, length, scan.offset);
      scan.offset += length;
      const lines = (scan.carry + buffer.toString("utf8")).split("\n");
      scan.carry = lines.pop();
      for (const line of lines) readLine(scan, line.replace(/\r$/, ""));
    }
    fs.closeSync(fd);
  }
  scan.mtime = stat.mtimeMs;
  scan.size = stat.size;
  return scan;
}

function liveGame(scan, name) {
  let game = scan.live.get(name);
  if (!game) scan.live.set(name, (game = { round: null, agendas: 0, turns: 0, thinkSeconds: 0 }));
  return game;
}

function readGameMentions(scan, line) {
  const turns = TURNS_SUMMARY.exec(line);
  if (turns) {
    if (NOT_SEATS.has(turns[2])) return;
    const game = liveGame(scan, turns[1]);
    game.turns += Number(turns[3]);
    game.thinkSeconds += Number(turns[3]) * Number(turns[4]);
    return;
  }
  const agenda = AGENDA_HEADER.exec(line);
  if (agenda) {
    const game = liveGame(scan, agenda[1]);
    game.round = Math.max(game.round ?? 0, Number(agenda[2]));
    game.agendas = Math.max(game.agendas, Number(agenda[3]));
    return;
  }
  const round = GAME_ROUND.exec(line);
  if (round) {
    const game = liveGame(scan, round[1]);
    game.round = Math.max(game.round ?? 0, Number(round[2] ?? round[3]));
  }
}

function readLine(scan, line) {
  if (scan.firstLine == null) scan.firstLine = line;
  if (line.includes("aiab") || line.includes("aiseed")) readGameMentions(scan, line);
  const game = GAME_LINE.exec(line);
  if (game) {
    const vp = Object.fromEntries(
      game[5].split(",").map(p => p.trim().split("=")).filter(p => p.length === 2).map(([f, v]) => [f, Number(v)]),
    );
    scan.games.push({
      index: Number(game[1]),
      total: Number(game[2]),
      end: game[3],
      round: Number(game[4]),
      vp,
      wallSeconds: Number(game[6]),
      cpuSeconds: game[7] ? Number(game[7]) : null,
    });
    return;
  }
  if (!line.startsWith("[")) return;
  if (line.includes("--- compiler:") && line.includes("testCompile")) scan.phase = "compiling tests";
  else if (line.includes("--- compiler:")) scan.phase = "compiling";
  else if (line.includes("--- surefire:")) scan.phase = "starting tests";
  else if (line.startsWith("[INFO] Running ")) {
    scan.phase = "playing";
    scan.playingSince ??= Date.now();
  } else if (line.includes("BUILD SUCCESS")) scan.build = "success";
  else if (line.includes("BUILD FAILURE")) scan.build = "failure";
  else if (line.startsWith("[ERROR]")) scan.errors++;
}

function playingStart(job, scan) {
  const stampMatch = scan?.firstLine?.match(/^(\d{4}-\d\d-\d\dT[\d:.]+Z)/);
  return job.startedAt ? Date.parse(job.startedAt) : stampMatch ? Date.parse(stampMatch[1]) : null;
}

function signature(args = []) {
  return args.filter(arg => !/^-Dai\.selfplay\.(seed|record\.label|record\.question)=/.test(arg)).join(" ");
}

function estimateMs(job, jobs) {
  if (job.estimateHours) return job.estimateHours * HOUR;
  const past = jobs
    .filter(o => o.status === "done" && signature(o.args) === signature(job.args))
    .map(o => Date.parse(o.finishedAt) - Date.parse(o.startedAt))
    .sort((a, b) => a - b);
  return past.length ? past[Math.floor(past.length / 2)] : null;
}

function slotGame(job, slot) {
  const seed = Number(argValue(job, "ai.selfplay.seed"));
  if (!Number.isFinite(seed)) return { name: null, seed: null, side: null };
  if (argValue(job, "ai.selfplay.ab")) {
    const pairSeed = seed + Math.floor(slot / 2);
    const side = slot % 2 === 0 ? "a" : "b";
    return { name: `aiab${((pairSeed % 1_000_000) + 1_000_000) % 1_000_000}${side}`, seed: pairSeed, side };
  }
  return { name: `aiseed${(((seed + slot) % 1_000_000) + 1_000_000) % 1_000_000}`, seed: seed + slot, side: null };
}

// Games start in slot order on a fixed pool of threads, so each finish frees a thread for the next slot. Replaying
// that with the finished games' wall times gives every game's start, measured from when the job started.
function gameRows(job, scan, total, started, now) {
  if (!total || !scan) return [];
  const finished = new Map(scan.games.map(game => [game.index - 1, game]));
  const threads = Number(argValue(job, "ai.selfplay.threads")) || Math.max(1, scan.live.size - finished.size) || 1;
  const running = job.status === "running";
  const startedCount = Math.min(total, finished.size + threads);
  const free = Array(threads).fill(started ?? now);
  const rows = [];
  for (let slot = 0; slot < total; slot++) {
    const game = slotGame(job, slot);
    const done = finished.get(slot);
    const live = game.name ? scan.live.get(game.name) : null;
    const row = {
      index: slot + 1,
      ...game,
      round: done?.round ?? live?.round ?? null,
      agendas: live?.agendas ?? 0,
      turns: live?.turns || null,
      thinkPerTurn: live?.turns ? live.thinkSeconds / live.turns : null,
    };
    if (slot >= startedCount && !done) {
      rows.push({ ...row, state: running ? "waiting" : "not played" });
      continue;
    }
    free.sort((a, b) => a - b);
    const start = free.shift();
    if (done) {
      free.push(start + done.wallSeconds * 1000);
      rows.push({ ...row, state: "finished", vp: done.vp, wallSeconds: done.wallSeconds, cpuSeconds: done.cpuSeconds, end: done.end });
    } else {
      free.push(Infinity);
      rows.push({ ...row, state: running ? "playing" : "unfinished", startedAt: start, wallSeconds: running ? Math.max(0, (now - start) / 1000) : null });
    }
  }
  return rows;
}

function progress(job, now) {
  const scan = scanLog(job.id);
  const total = Number(argValue(job, "ai.selfplay.games")) || scan?.games[0]?.total || null;
  const games = scan?.games ?? [];
  const started = playingStart(job, scan);
  let projectedFinish = null;
  if (job.status === "running" && total && games.length >= 1 && started) {
    const perGame = (now - started) / games.length;
    projectedFinish = now + perGame * (total - games.length);
  }
  return {
    total,
    finished: games.length,
    games,
    rows: gameRows(job, scan, total, started, now),
    phase: scan ? (job.status === "running" ? scan.phase : scan.build ?? scan.phase) : null,
    errors: scan?.errors ?? 0,
    logBytes: scan?.size ?? 0,
    lastOutputAt: scan?.mtime ?? null,
    projectedFinish,
  };
}

function state() {
  const now = Date.now();
  const jobs = readJobs();
  const view = jobs.map(job => {
    const estimate = estimateMs(job, jobs);
    const base = {
      id: job.id,
      label: job.label,
      question: job.question,
      status: job.status,
      by: job.submittedBy,
      branch: job.branch,
      commit: job.commit?.slice(0, 10),
      commitSubject: job.commitSubject,
      ab: argValue(job, "ai.selfplay.ab") ?? null,
      seed: argValue(job, "ai.selfplay.seed") ?? null,
      threads: argValue(job, "ai.selfplay.threads") ?? null,
      args: job.mavenArguments ?? job.args,
      estimateMs: estimate,
      submittedAt: job.submittedAt,
      approvedAt: job.approvedAt,
      startedAt: job.startedAt ?? null,
      finishedAt: job.finishedAt ?? null,
      exitCode: job.exitCode ?? null,
      cancelRequested: Boolean(job.cancelRequested),
      records: job.records ?? [],
      load: job.load ?? null,
      hasLog: fs.existsSync(logPath(job.id)),
    };
    if (job.status === "running" || FINISHED.has(job.status)) base.progress = progress(job, now);
    return base;
  });
  let cursor = now;
  for (const job of view.filter(j => j.status === "running")) {
    const byEstimate = job.estimateMs == null ? null : Date.parse(job.startedAt) + job.estimateMs;
    const finish = job.progress?.projectedFinish ?? byEstimate;
    job.eta = { start: Date.parse(job.startedAt), finish: finish == null ? null : Math.max(finish, now + 60_000), byEstimate };
    cursor = job.eta.finish == null ? null : Math.max(cursor ?? 0, job.eta.finish);
  }
  const queued = view.filter(j => j.status === "queued").sort((a, b) => a.approvedAt.localeCompare(b.approvedAt));
  queued.forEach((job, position) => {
    const finish = cursor == null || job.estimateMs == null ? null : cursor + job.estimateMs;
    job.eta = { start: cursor, finish };
    job.position = position + 1;
    cursor = finish;
  });
  return { now, queueDir: QUEUE, runner: runner(), cpu: cpuPercent(), jobs: view };
}

let lastCpu = os.cpus();
let lastCpuPercent = null;
function cpuPercent() {
  const current = os.cpus();
  let idle = 0;
  let total = 0;
  current.forEach((cpu, i) => {
    const before = lastCpu[i]?.times;
    if (!before) return;
    for (const key of Object.keys(cpu.times)) total += cpu.times[key] - before[key];
    idle += cpu.times.idle - before.idle;
  });
  if (total > 0) {
    lastCpuPercent = Math.round(100 * (1 - idle / total));
    lastCpu = current;
  }
  return { percent: lastCpuPercent, cores: current.length };
}

const NEWLINE = 0x0a;
const MAX_LOG_RESPONSE = 2 * 1024 * 1024;

// A game's lines are those naming it, its "Game N/M finished" line, and the indented lines that follow a line naming
// it (the body of its AGENDA TRACE blocks). The block owner is carried between polls by the client.
function gameLines(job, game, after, block) {
  const slot = Array.from({ length: Number(argValue(job, "ai.selfplay.games")) || 0 }, (_, s) => s).find(
    s => slotGame(job, s).name === game,
  );
  const finishedPrefix = slot == null ? null : `Game ${slot + 1}/`;
  const all = logLines(job.id, after < 0 ? 0 : after, 0, Infinity);
  if (all.missing) return all;
  const naming = new RegExp(`(?<![A-Za-z0-9])${game}(?![A-Za-z0-9])`);
  const lines = [];
  let owner = after < 0 ? null : block || null;
  for (const line of all.lines) {
    const mentions = (line.includes(game) && naming.test(line)) || (finishedPrefix && line.startsWith(finishedPrefix) && line.includes(" finished: "));
    if (!/^\s/.test(line)) owner = mentions ? game : null;
    if (mentions || owner === game) lines.push(line);
  }
  return { offset: all.offset, lines: after < 0 ? lines.slice(-4000) : lines, block: owner ?? "" };
}

function logLines(id, after, backlogBytes, maxBytes = MAX_LOG_RESPONSE) {
  const file = logPath(id);
  let size;
  try {
    size = fs.statSync(file).size;
  } catch {
    return { offset: 0, lines: [], missing: true };
  }
  const fromTail = after < 0 || after > size;
  let start = fromTail ? Math.max(0, size - backlogBytes) : after;
  const length = Math.min(size - start, maxBytes);
  if (length <= 0) return { offset: start, lines: [] };
  const buffer = Buffer.alloc(length);
  const fd = fs.openSync(file, "r");
  fs.readSync(fd, buffer, 0, length, start);
  fs.closeSync(fd);
  let from = 0;
  if (fromTail && start > 0) from = buffer.indexOf(NEWLINE) + 1;
  const end = buffer.lastIndexOf(NEWLINE) + 1;
  if (end <= from) return { offset: start + from, lines: [] };
  const lines = buffer.toString("utf8", from, end).split("\n");
  lines.pop();
  return { offset: start + end, lines: lines.map(l => l.replace(/\r$/, "")) };
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, "http://localhost");
  try {
    if (url.pathname === "/") {
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      res.end(PAGE);
    } else if (url.pathname === "/api/state") {
      res.writeHead(200, { "Content-Type": "application/json", "Cache-Control": "no-cache" });
      res.end(JSON.stringify(state()));
    } else if (url.pathname === "/api/output") {
      const id = url.searchParams.get("id") ?? "";
      const job = readJobs().find(job => job.id === id);
      if (!job) {
        res.writeHead(404).end("no such job");
        return;
      }
      const after = Number(url.searchParams.get("after") ?? -1);
      const game = url.searchParams.get("game");
      if (game && !/^(aiab\d+[ab]|aiseed\d+)$/.test(game)) {
        res.writeHead(400).end("bad game name");
        return;
      }
      const result = game
        ? gameLines(job, game, after, url.searchParams.get("block"))
        : logLines(id, after, Number(url.searchParams.get("backlog") ?? 262_144));
      res.writeHead(200, { "Content-Type": "application/json", "Cache-Control": "no-cache" });
      res.end(JSON.stringify(result));
    } else {
      res.writeHead(404).end("not found");
    }
  } catch (error) {
    res.writeHead(500).end(String(error.stack ?? error));
  }
});

server.listen(port, "127.0.0.1", () => {
  console.log(`AI A/B dashboard on http://127.0.0.1:${port} (queue ${QUEUE})`);
});

const PAGE = String.raw`<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AI A/B Queue</title>
<style>
:root {
  --bg: #f6f7f9; --panel: #ffffff; --line: #e3e6eb; --text: #1c2230; --muted: #667085;
  --accent: #3b6ee8; --accent-soft: #e6edfd; --ok: #1f8a4c; --ok-soft: #e3f4ea; --warn: #b7791f; --warn-soft: #fbf0dc;
  --bad: #c2393b; --bad-soft: #fbe4e4; --idle: #8a93a5; --idle-soft: #eef0f4; --log-bg: #0f1420; --log-text: #d6dbe6;
  --hl-game: #7ee2a8; --hl-err: #ff8c8c; --hl-info: #8fb3ff; --hl-warn: #f3c96b;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #0d1117; --panel: #151b24; --line: #263040; --text: #e5e9f0; --muted: #8b95a7;
    --accent: #6d97ff; --accent-soft: #1b2a4a; --ok: #4cc47e; --ok-soft: #15301f; --warn: #e0a84a; --warn-soft: #33270f;
    --bad: #ff6b6d; --bad-soft: #3a1618; --idle: #7f899b; --idle-soft: #1d2430; --log-bg: #0a0e15;
  }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 14px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; }
header { display: flex; flex-wrap: wrap; gap: 12px 24px; align-items: baseline; padding: 16px 20px 8px; }
h1 { font-size: 18px; margin: 0; }
h2 { font-size: 13px; text-transform: uppercase; letter-spacing: .06em; color: var(--muted); margin: 22px 0 8px; }
.meta { color: var(--muted); font-size: 13px; display: flex; gap: 18px; flex-wrap: wrap; }
.meta b { color: var(--text); font-weight: 600; }
main { padding: 0 20px 24px; max-width: 1500px; margin: 0 auto; }
.grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.25fr); grid-template-areas: "run log" "rest log"; grid-template-rows: auto 1fr; gap: 0 20px; align-items: start; }
#runSec { grid-area: run; } #logSec { grid-area: log; align-self: stretch; } #restSec { grid-area: rest; }
#logSec .log-card { margin-top: 22px; }
@media (max-width: 1050px) {
  .grid { grid-template-columns: minmax(0, 1fr); grid-template-areas: "run" "log" "rest"; }
  .log-card { position: static; height: 70vh; }
}
.card { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 14px 16px; margin-bottom: 12px; }
.card.selectable { cursor: pointer; }
.card.selected { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent); }
.row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.title { font-weight: 600; font-size: 15px; word-break: break-word; }
.question { color: var(--muted); margin: 4px 0 8px; }
.pill { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }
.s-running { background: var(--accent-soft); color: var(--accent); }
.s-queued { background: var(--idle-soft); color: var(--text); }
.s-awaiting-approval { background: var(--warn-soft); color: var(--warn); }
.s-done { background: var(--ok-soft); color: var(--ok); }
.s-failed, .s-interrupted { background: var(--bad-soft); color: var(--bad); }
.s-cancelled { background: var(--idle-soft); color: var(--idle); }
.kv { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 6px 16px; margin-top: 10px; font-size: 13px; }
.kv div span { display: block; color: var(--muted); font-size: 11px; text-transform: uppercase; letter-spacing: .04em; }
.bar { height: 10px; background: var(--idle-soft); border-radius: 999px; overflow: hidden; margin: 10px 0 4px; }
.bar > i { display: block; height: 100%; background: var(--accent); border-radius: 999px; transition: width .6s; }
.bar.est > i { background: repeating-linear-gradient(45deg, var(--accent), var(--accent) 6px, var(--accent-soft) 6px, var(--accent-soft) 12px); }
.small { font-size: 12px; color: var(--muted); }
.stale { color: var(--warn); font-weight: 600; }
table { border-collapse: collapse; width: 100%; font-size: 12.5px; margin-top: 10px; font-variant-numeric: tabular-nums; }
th, td { text-align: left; padding: 4px 7px; border-bottom: 1px solid var(--line); white-space: nowrap; }
th { color: var(--muted); font-weight: 600; font-size: 11px; text-transform: uppercase; }
td.win { color: var(--ok); font-weight: 700; }
.tablewrap { overflow-x: auto; }
tr.game-row { cursor: pointer; }
tr.game-row:hover td { background: var(--accent-soft); }
tr.game-row.selected td { background: var(--accent-soft); box-shadow: inset 0 1px 0 var(--accent), inset 0 -1px 0 var(--accent); }
tr.dim td { color: var(--muted); }
th.split, td.split { border-left: 1px solid var(--line); }
td .pill { font-size: 10.5px; }
.chip { display: inline-flex; gap: 6px; align-items: center; background: var(--accent-soft); color: var(--accent); border-radius: 999px; padding: 2px 4px 2px 10px; font-size: 12.5px; font-weight: 600; }
.chip[hidden] { display: none; }
.chip button { font: inherit; font-size: 11.5px; border: 0; border-radius: 999px; padding: 2px 8px; background: var(--panel); color: var(--text); cursor: pointer; }
.queue-item { display: grid; grid-template-columns: 28px 1fr auto; gap: 10px; align-items: center; }
.pos { width: 24px; height: 24px; border-radius: 50%; background: var(--idle-soft); display: grid; place-items: center; font-size: 12px; font-weight: 700; }
.log-card { position: sticky; top: 12px; padding: 0; overflow: hidden; display: flex; flex-direction: column; height: calc(100vh - 24px); min-height: 420px; }
.log-head { padding: 10px 14px; border-bottom: 1px solid var(--line); display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.log-head select, .log-head input { font: inherit; font-size: 13px; padding: 4px 8px; border-radius: 6px; border: 1px solid var(--line); background: var(--bg); color: var(--text); }
.log-head input[type=text] { flex: 1; min-width: 120px; }
.log-head label { font-size: 12.5px; color: var(--muted); display: flex; gap: 4px; align-items: center; white-space: nowrap; }
#log { flex: 1; overflow: auto; margin: 0; padding: 10px 14px; background: var(--log-bg); color: var(--log-text); font: 12px/1.45 ui-monospace, "Cascadia Mono", Consolas, monospace; white-space: pre-wrap; word-break: break-all; }
#log .g { color: var(--hl-game); font-weight: 700; }
#log .e { color: var(--hl-err); }
#log .i { color: var(--hl-info); }
#log .w { color: var(--hl-warn); }
.empty { color: var(--muted); font-style: italic; padding: 6px 0; }
details summary { cursor: pointer; color: var(--muted); font-size: 12.5px; margin-top: 8px; }
code { font: 11.5px ui-monospace, Consolas, monospace; word-break: break-all; }
</style>
</head>
<body>
<header>
  <h1>AI A/B Queue</h1>
  <div class="meta" id="meta"></div>
</header>
<main class="grid">
  <section id="runSec">
    <h2>Running</h2><div id="running"></div>
  </section>
  <section id="restSec">
    <h2>Queued</h2><div id="queued"></div>
    <h2>Awaiting approval</h2><div id="awaiting"></div>
    <h2>Recently finished</h2><div id="finished"></div>
  </section>
  <section id="logSec">
    <div class="card log-card">
      <div class="log-head">
        <select id="logJob"></select>
        <input type="text" id="filter" placeholder="Filter lines (regex)">
        <label><input type="checkbox" id="highlights"> Highlights only</label>
        <label><input type="checkbox" id="follow" checked> Follow</label>
        <span id="gameChip" class="chip" hidden><span id="gameChipName"></span> <button id="gameChipClear" title="Show the whole log">✕ whole log</button></span>
        <span id="logStatus" class="small"></span>
      </div>
      <pre id="log"></pre>
    </div>
  </section>
</main>
<script>
const MAX_LINES = 4000;
let data = null, logJob = null, lines = [], selectedByUser = false;
const $ = id => document.getElementById(id);
const esc = s => String(s ?? "").replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
const pad = n => String(n).padStart(2, "0");
function clock(ms, seconds = false) {
  if (ms == null || isNaN(ms)) return "?";
  const d = new Date(ms), today = new Date();
  const time = d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit", second: seconds ? "2-digit" : undefined, hour12: true });
  return d.toDateString() === today.toDateString() ? time : (d.getMonth() + 1) + "/" + d.getDate() + " " + time;
}
const ZONE = new Date().toLocaleTimeString([], { timeZoneName: "short" }).split(" ").pop();
const localStamp = l => l.replace(/^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z)/, s => clock(Date.parse(s), true));
function dur(ms) {
  if (ms == null || isNaN(ms)) return "?";
  const m = Math.round(Math.abs(ms) / 60000);
  return m < 60 ? m + " min" : Math.floor(m / 60) + " h " + pad(m % 60) + " m";
}
const pill = s => '<span class="pill s-' + s + '">' + s.replace("-", " ") + "</span>";

function runningCard(j) {
  const p = j.progress || {}, now = data.now;
  const elapsed = now - Date.parse(j.startedAt);
  const hasGames = p.total && p.finished > 0;
  const pct = hasGames ? 100 * p.finished / p.total : j.estimateMs ? Math.min(99, 100 * elapsed / j.estimateMs) : 0;
  const quiet = p.lastOutputAt ? now - p.lastOutputAt : null;
  const games = p.games || [];
  const avgRound = games.length ? (games.reduce((a, g) => a + g.round, 0) / games.length).toFixed(1) : "-";
  const avgWall = games.length ? games.reduce((a, g) => a + g.wallSeconds, 0) / games.length * 1000 : null;
  const leaderVp = games.length ? (games.reduce((a, g) => a + Math.max(...Object.values(g.vp)), 0) / games.length).toFixed(1) : "-";
  return '<div class="card selectable' + (logJob === j.id ? " selected" : "") + '" data-job="' + esc(j.id) + '">' +
    '<div class="row">' + pill(j.status) + (j.cancelRequested ? pill("cancelled") : "") +
    '<span class="title">' + esc(j.label) + "</span>" + (j.ab ? '<span class="pill s-queued">A/B ' + esc(j.ab) + "</span>" : "") + "</div>" +
    (j.question ? '<div class="question">' + esc(j.question) + "</div>" : "") +
    '<div class="bar' + (hasGames ? "" : " est") + '"><i style="width:' + pct.toFixed(1) + '%"></i></div>' +
    '<div class="row small"><b style="color:var(--text)">' + (hasGames ? p.finished + " / " + p.total + " games" : (p.total ? "0 / " + p.total + " games" : "") + " " + (p.phase || "")) +
    "</b><span>" + pct.toFixed(0) + "%" + (hasGames ? "" : " of estimate") + "</span>" +
    (quiet != null ? '<span class="' + (quiet > 20 * 60000 ? "stale" : "") + '">last output ' + dur(quiet) + " ago</span>" : "") + "</div>" +
    '<div class="kv">' +
    "<div><span>Started</span>" + clock(Date.parse(j.startedAt)) + " (" + dur(elapsed) + ")</div>" +
    "<div><span>Finish (by pace)</span>" + (p.projectedFinish ? clock(j.eta.finish) + " (in " + dur(j.eta.finish - now) + ")" : "after first game") + "</div>" +
    "<div><span>Finish (by estimate)</span>" + (j.eta?.byEstimate ? clock(j.eta.byEstimate) : "?") + "</div>" +
    "<div><span>Phase</span>" + esc(p.phase || "?") + "</div>" +
    "<div><span>Seeds / threads</span>" + esc(j.seed ?? "-") + " / " + esc(j.threads ?? "-") + "</div>" +
    "<div><span>Avg round / leader VP</span>" + avgRound + " / " + leaderVp + "</div>" +
    "<div><span>Avg game wall</span>" + (avgWall ? dur(avgWall) : "-") + "</div>" +
    "<div><span>Commit</span><code>" + esc(j.commit) + "</code> " + esc(j.branch) + "</div>" +
    "<div><span>By</span>" + esc(j.by || "-") + "</div>" +
    (p.errors ? '<div><span>[ERROR] lines</span><b style="color:var(--bad)">' + p.errors + "</b></div>" : "") +
    "</div>" + gamesTable(j, true) + argsBlock(j) + "</div>";
}

const openTables = new Set();
const tableScroll = new Map();

function gamesTable(j, openByDefault) {
  const rows = j.progress?.rows || [];
  if (!rows.length) return "";
  const factions = [...new Set(rows.flatMap(g => Object.keys(g.vp || {})))];
  const counts = {};
  rows.forEach(r => (counts[r.state] = (counts[r.state] || 0) + 1));
  const order = { playing: 0, finished: 1, unfinished: 1, waiting: 2, "not played": 2 };
  const body = [...rows].sort((a, b) => order[a.state] - order[b.state] || a.index - b.index).map(g => {
    const top = g.vp ? Math.max(...Object.values(g.vp)) : null;
    const clickable = g.name && g.state !== "waiting" && g.state !== "not played";
    const attrs = clickable ? ' class="game-row' + (logJob === j.id && logGame === g.name ? " selected" : "") + '" data-game="' + esc(g.name) + '" data-game-job="' + esc(j.id) + '" title="Show only this game\'s log lines"' : ' class="dim"';
    const time = g.wallSeconds == null ? "" : (g.state === "playing" ? "~" : "") + dur(g.wallSeconds * 1000);
    const perRound = g.wallSeconds && g.round ? Math.round(g.wallSeconds / 60 / g.round) + " min" : "";
    const label = g.end && g.end !== "VICTORY_POINTS" ? g.end.replace(/_/g, " ").toLowerCase() : g.state;
    const think = g.thinkPerTurn == null ? "" : g.turns + " / " + g.thinkPerTurn.toFixed(1) + "s";
    return "<tr" + attrs + "><td>" + g.index + "</td>" +
      '<td><span class="pill s-' + (g.state === "playing" ? "running" : g.state === "finished" && label === "finished" ? "done" : g.state === "finished" || g.state === "unfinished" ? "failed" : "queued") + '">' + esc(label) + "</span></td>" +
      "<td>" + (g.round ?? "") + "</td>" +
      factions.map(f => '<td class="' + (g.vp && g.vp[f] === top ? "win" : "") + '">' + (g.vp?.[f] ?? "") + "</td>").join("") +
      '<td class="split"><b>' + time + "</b></td><td>" + perRound + "</td>" +
      "<td>" + (g.cpuSeconds != null ? dur(g.cpuSeconds * 1000) : "") + "</td><td>" + think + "</td></tr>";
  }).join("");
  const summary = ["playing", "finished", "unfinished", "waiting", "not played"].filter(s => counts[s]).map(s => counts[s] + " " + s).join(" · ");
  const open = openTables.has(j.id) || (openByDefault && !openTables.has("closed:" + j.id));
  return '<details data-table="' + esc(j.id) + '"' + (open ? " open" : "") + '><summary>Games: ' + summary + ' <span class="small">(click a game for its log)</span></summary><div class="tablewrap" data-scroll="' + esc(j.id) + '"><table><tr><th>Game</th><th>Status</th><th>Round</th>' +
    factions.map(f => '<th title="' + esc(f) + ' victory points">' + esc(f) + "</th>").join("") +
    '<th class="split" title="How long the game took, or has been running (~)">Time</th><th title="Average real time per round">Per round</th><th title="CPU time of the game\'s thread">CPU</th><th title="Faction turns, and average AI thinking per turn">Turns / think</th></tr>' + body + "</table></div></details>";
}

function argsBlock(j) {
  return '<details><summary>Maven arguments</summary><code>' + esc((j.args || []).join(" ")) + "</code></details>";
}

function queuedCard(j) {
  const when = j.status === "awaiting-approval" ? "needs the user's yes" :
    "starts ~" + clock(j.eta?.start) + ", finishes ~" + clock(j.eta?.finish);
  return '<div class="card"><div class="queue-item"><div class="pos">' + (j.position ?? "?") + "</div><div>" +
    '<div class="row"><span class="title">' + esc(j.label) + "</span>" + pill(j.status) + (j.ab ? '<span class="pill s-queued">A/B ' + esc(j.ab) + "</span>" : "") + "</div>" +
    (j.question ? '<div class="question">' + esc(j.question) + "</div>" : "") +
    '<div class="small">' + when + " · est " + (j.estimateMs ? dur(j.estimateMs) : "?") + " · " + esc(j.by || "") + " · <code>" + esc(j.commit) + "</code> " + esc(j.branch) + "</div>" +
    "</div><div></div></div></div>";
}

function finishedCard(j) {
  const p = j.progress || {};
  const ran = j.startedAt && j.finishedAt ? dur(Date.parse(j.finishedAt) - Date.parse(j.startedAt)) : "-";
  return '<div class="card selectable' + (logJob === j.id ? " selected" : "") + '" data-job="' + esc(j.id) + '">' +
    '<div class="row">' + pill(j.status) + '<span class="title">' + esc(j.label) + "</span>" +
    '<span class="small">' + clock(Date.parse(j.finishedAt)) + " · ran " + ran + (p.total ? " · " + p.finished + "/" + p.total + " games" : "") +
    (j.exitCode != null ? " · exit " + j.exitCode : "") + "</span></div>" +
    (j.question ? '<div class="question">' + esc(j.question) + "</div>" : "") +
    (j.records?.length ? '<div class="small">Record: <code>' + j.records.map(esc).join("</code>, <code>") + "</code></div>" : "") +
    gamesTable(j, false) + "</div>";
}

function render() {
  const jobs = data.jobs;
  const running = jobs.filter(j => j.status === "running");
  const queued = jobs.filter(j => j.status === "queued").sort((a, b) => a.position - b.position);
  const awaiting = jobs.filter(j => j.status === "awaiting-approval");
  const finished = jobs.filter(j => ["done", "failed", "cancelled", "interrupted"].includes(j.status))
    .sort((a, b) => (b.finishedAt || "").localeCompare(a.finishedAt || "")).slice(0, 8);
  const last = queued.length ? queued[queued.length - 1].eta?.finish : running[0]?.eta?.finish;
  $("meta").innerHTML =
    "<span>Runner: <b>" + (data.runner ? "pid " + data.runner.pid + " since " + clock(Date.parse(data.runner.startedAt)) : "idle") + "</b></span>" +
    "<span>CPU: <b>" + (data.cpu.percent ?? "?") + "%</b> of " + data.cpu.cores + " threads</span>" +
    "<span>Queue clears: <b>" + (running.length || queued.length ? clock(last) : "now") + "</b></span>" +
    "<span>Updated " + clock(data.now, true) + " " + ZONE + "</span>";
  $("running").innerHTML = running.map(runningCard).join("") || '<div class="empty">Nothing running.</div>';
  $("queued").innerHTML = queued.map(queuedCard).join("") || '<div class="empty">Queue is empty.</div>';
  $("awaiting").innerHTML = awaiting.map(queuedCard).join("") || '<div class="empty">None.</div>';
  $("finished").innerHTML = finished.map(finishedCard).join("") || '<div class="empty">None yet.</div>';
  document.querySelectorAll("[data-job]").forEach(el => el.addEventListener("click", e => {
    if (e.target.closest("details")) return;
    selectedByUser = true;
    openLog(el.dataset.job, null);
  }));
  document.querySelectorAll("[data-game]").forEach(el => el.addEventListener("click", () => {
    selectedByUser = true;
    openLog(el.dataset.gameJob, el.dataset.game);
  }));
  document.querySelectorAll("[data-scroll]").forEach(el => {
    el.scrollLeft = tableScroll.get(el.dataset.scroll) || 0;
    el.addEventListener("scroll", () => tableScroll.set(el.dataset.scroll, el.scrollLeft), { passive: true });
  });
  document.querySelectorAll("details[data-table]").forEach(el => el.addEventListener("toggle", () => {
    const id = el.dataset.table;
    openTables.delete(el.open ? "closed:" + id : id);
    openTables.add(el.open ? id : "closed:" + id);
  }));
  const withLogs = jobs.filter(j => j.hasLog).sort((a, b) => (b.startedAt || "").localeCompare(a.startedAt || ""));
  const select = $("logJob");
  const options = withLogs.map(j => '<option value="' + esc(j.id) + '">' + esc(j.label) + " (" + j.status + ")</option>").join("");
  if (select.dataset.options !== options) { select.innerHTML = options; select.dataset.options = options; }
  if (!selectedByUser || !logJob) {
    const pick = running[0]?.id || withLogs[0]?.id;
    if (pick && pick !== logJob) openLog(pick, null);
  }
  if (logJob) select.value = logJob;
}

async function refresh() {
  try {
    data = await (await fetch("/api/state")).json();
    render();
  } catch (e) {
    $("meta").innerHTML = '<span class="stale">Dashboard server unreachable</span>';
  }
}

function lineClass(l) {
  if (/^Game \d+\/\d+ finished/.test(l)) return "g";
  if (/^\[ERROR\]|BUILD FAILURE|^(Exception|Caused by)|^[\w.$]*(Exception|Error)(:|\s|$)|runner error/.test(l)) return "e";
  if (/^\[WARNING\]|^WARNING/.test(l)) return "w";
  if (/^\[INFO\]|BUILD SUCCESS|^(\d+\/\d+ )?\d{1,2}:\d\d:\d\d\s?[AP]M/i.test(l)) return "i";
  return "";
}

function filterRegex() {
  const text = $("filter").value.trim();
  if (!text) return null;
  try { return new RegExp(text, "i"); } catch { return null; }
}

function visible(l, re) {
  if ($("highlights").checked && !lineClass(l)) return false;
  return !re || re.test(l);
}

function lineHtml(l) {
  const c = lineClass(l);
  return c ? '<span class="' + c + '">' + esc(l) + "</span>\n" : esc(l) + "\n";
}

function redraw() {
  const re = filterRegex();
  $("log").innerHTML = lines.filter(l => visible(l, re)).map(lineHtml).join("");
  if ($("follow").checked) $("log").scrollTop = $("log").scrollHeight;
}

function setLogStatus(text, bad) {
  const el = $("logStatus");
  el.textContent = text;
  el.className = "small" + (bad ? " stale" : "");
}

function appendLines(added) {
  if (!lines.length) $("log").textContent = "";
  lines.push(...added);
  if (lines.length > MAX_LINES) { lines = lines.slice(-MAX_LINES); redraw(); return; }
  const re = filterRegex();
  const html = added.filter(l => visible(l, re)).map(lineHtml).join("");
  if (html) $("log").insertAdjacentHTML("beforeend", html);
  if ($("follow").checked) $("log").scrollTop = $("log").scrollHeight;
}

async function pollLog(id, game, token) {
  let offset = -1, block = "";
  while (token === logToken) {
    let more = false;
    let response = null;
    try {
      const url = "/api/output?id=" + encodeURIComponent(id) + "&after=" + offset +
        (game ? "&game=" + encodeURIComponent(game) + "&block=" + encodeURIComponent(block) : "");
      response = await fetch(url, { cache: "no-store" });
    } catch (e) {
      setLogStatus("can't reach the dashboard server (" + e.message + "), retrying…", true);
    }
    if (response) {
      try {
        if (!response.ok) throw new Error("server answered " + response.status + " " + (await response.text()).slice(0, 80));
        const result = await response.json();
        if (token !== logToken) return;
        if (result.missing) {
          setLogStatus("no log file yet", false);
        } else {
          more = result.offset > offset && result.lines.length > 2000;
          offset = result.offset;
          block = result.block ?? "";
          if (result.lines.length) appendLines(result.lines.map(localStamp));
          else if (!lines.length) $("log").textContent = game ? "No lines for this game yet." : "The log is empty so far.";
          setLogStatus("live · " + clock(Date.now(), true), false);
        }
      } catch (e) {
        setLogStatus("log error: " + e.message, true);
      }
    }
    await new Promise(r => setTimeout(r, more ? 50 : 1000));
  }
}

let logToken = 0, logGame = null;
function openLog(id, game) {
  logJob = id;
  logGame = game || null;
  lines = [];
  $("log").textContent = "Loading log…";
  document.querySelectorAll("[data-job]").forEach(el => el.classList.toggle("selected", el.dataset.job === id));
  document.querySelectorAll("[data-game]").forEach(el => el.classList.toggle("selected", el.dataset.gameJob === id && el.dataset.game === logGame));
  $("logJob").value = id;
  $("gameChip").hidden = !logGame;
  $("gameChipName").textContent = logGame ? "Game " + logGame : "";
  pollLog(id, logGame, ++logToken);
}

$("logJob").addEventListener("change", e => { selectedByUser = true; openLog(e.target.value, null); });
$("gameChipClear").addEventListener("click", () => openLog(logJob, null));
$("filter").addEventListener("input", redraw);
$("highlights").addEventListener("change", redraw);
$("follow").addEventListener("change", redraw);
let userScrollAt = 0;
["wheel", "mousedown", "keydown", "touchmove"].forEach(type => $("log").addEventListener(type, () => (userScrollAt = Date.now()), { passive: true }));
$("log").addEventListener("scroll", () => {
  if (Date.now() - userScrollAt > 1500) return;
  const el = $("log");
  const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 30;
  if ($("follow").checked !== atBottom) $("follow").checked = atBottom;
});
refresh();
setInterval(refresh, 5000);
</script>
</body>
</html>`;
