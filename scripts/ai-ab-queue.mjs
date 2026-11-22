#!/usr/bin/env node
// One queue for every AI A/B and benchmark run on this machine, so runs never overlap: overlapping batches slow
// each other, make wall times meaningless and push self-play games into their time limits.
//
// The queue lives in the repository's common git folder (<repo>/.git/ai-ab-queue), which every worktree shares.
// A job is a committed worktree state plus Maven arguments. It waits for the user's yes ("awaiting-approval"), then
// a single runner plays the jobs one at a time, each from its own detached checkout of the job's commit
// (<repo>/.claude/worktrees/ab-run-<id>), so the submitting session can keep editing and building. The run is
// recorded in the submitting worktree's docs/ai/experiments, and the runner adds a "queue" section to its run.json
// and README.md: queue wait, run time and the other builds that shared the machine.
//
//   node scripts/ai-ab-queue.mjs submit --label <name> [--question "..."] [--hours <estimate>] [--approved]
//                                       [--by <session>] -- <maven arguments>
//   node scripts/ai-ab-queue.mjs approve <id>     the user said yes: queue it and start the runner if none is up
//   node scripts/ai-ab-queue.mjs list [--all]     what runs, what waits, and when each should start and finish
//   node scripts/ai-ab-queue.mjs wait <id>        blocks until the job ends; run it in the background to be told
//   node scripts/ai-ab-queue.mjs show <id>        the job's full record
//   node scripts/ai-ab-queue.mjs cancel <id>      drops a waiting job, or stops a running one (whole process tree)
//   node scripts/ai-ab-queue.mjs run              runs queued jobs until none are left (approve starts this)
//
// Ids can be shortened to any unique part. --approved is only for a run the user has already said yes to.
// Environment: AI_AB_QUEUE_DIR moves the queue, AI_AB_QUEUE_MVN replaces "mvn", AI_AB_QUEUE_NO_AUTOSTART=1 keeps
// approve from starting a runner, AI_AB_QUEUE_SAMPLE_SECONDS sets the load sampling interval (0 turns it off).
import { execFileSync, spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SCRIPT = fileURLToPath(import.meta.url);
const HOUR = 3_600_000;
const FINISHED = new Set(["done", "failed", "cancelled", "interrupted"]);
const QUEUE_PROPERTY = "ai.selfplay.queue.id";
const CODE_PATHS = ["src", "pom.xml"];

export function git(cwd, ...args) {
  return execFileSync("git", ["-C", cwd, ...args], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
}

function commonDir(cwd) {
  return git(cwd, "rev-parse", "--path-format=absolute", "--git-common-dir");
}

export function queueDir(cwd = process.cwd()) {
  if (process.env.AI_AB_QUEUE_DIR) return path.resolve(process.env.AI_AB_QUEUE_DIR);
  return path.join(commonDir(cwd), "ai-ab-queue");
}

function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const temp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(temp, JSON.stringify(value, null, 2) + "\n");
  fs.renameSync(temp, file);
}

const jobFile = (queue, id) => path.join(queue, "jobs", `${id}.json`);

export function readJobs(queue) {
  const dir = path.join(queue, "jobs");
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter(name => name.endsWith(".json"))
    .map(name => JSON.parse(fs.readFileSync(path.join(dir, name), "utf8")))
    .sort((a, b) => a.submittedAt.localeCompare(b.submittedAt));
}

export function readJob(queue, id) {
  return JSON.parse(fs.readFileSync(jobFile(queue, id), "utf8"));
}

function updateJob(queue, id, change) {
  const job = readJob(queue, id);
  change(job);
  writeJson(jobFile(queue, id), job);
  return job;
}

export function resolveId(queue, text) {
  const jobs = readJobs(queue);
  const exact = jobs.find(job => job.id === text);
  if (exact) return exact.id;
  const matches = jobs.filter(job => job.id.includes(text));
  if (matches.length === 1) return matches[0].id;
  throw new Error(matches.length ? `"${text}" matches ${matches.map(j => j.id).join(", ")}` : `no job "${text}"`);
}

function slug(text) {
  return text.toLowerCase().replace(/[^a-z0-9+]+/g, "-").replace(/(^-|-$)/g, "").slice(0, 40) || "run";
}

function stamp(date) {
  const pad = n => String(n).padStart(2, "0");
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(
    date.getMinutes(),
  )}${pad(date.getSeconds())}`;
}

export function submit(cwd, { label, question = "", hours = null, approved = false, by = "", args = [] }) {
  if (!label) throw new Error("submit needs --label");
  if (!args.length) throw new Error("submit needs Maven arguments after --");
  const worktree = git(cwd, "rev-parse", "--show-toplevel");
  const dirty = git(worktree, "status", "--porcelain", "--untracked-files=all", "--", ...CODE_PATHS);
  if (dirty) {
    throw new Error(`commit first: the runner plays the commit, not these changes:\n${dirty}`);
  }
  const queue = queueDir(worktree);
  const now = new Date();
  let id = `${stamp(now)}-${slug(label)}`;
  for (let n = 2; fs.existsSync(jobFile(queue, id)); n++) id = `${stamp(now)}-${slug(label)}-${n}`;
  const job = {
    id,
    label,
    question,
    submittedBy: by,
    worktree,
    branch: git(worktree, "rev-parse", "--abbrev-ref", "HEAD"),
    commit: git(worktree, "rev-parse", "HEAD"),
    commitSubject: git(worktree, "log", "-1", "--format=%s"),
    args,
    estimateHours: hours,
    status: approved ? "queued" : "awaiting-approval",
    submittedAt: now.toISOString(),
    approvedAt: approved ? now.toISOString() : null,
  };
  writeJson(jobFile(queue, id), job);
  if (approved) ensureRunner(queue);
  return job;
}

export function approve(queue, id) {
  const job = updateJob(queue, id, job => {
    if (job.status !== "awaiting-approval") throw new Error(`${id} is ${job.status}`);
    job.status = "queued";
    job.approvedAt = new Date().toISOString();
  });
  ensureRunner(queue);
  return job;
}

export function cancel(queue, id) {
  return updateJob(queue, id, job => {
    if (FINISHED.has(job.status)) throw new Error(`${id} is already ${job.status}`);
    if (job.status === "running") {
      job.cancelRequested = true;
    } else {
      job.status = "cancelled";
      job.finishedAt = new Date().toISOString();
    }
  });
}

function signature(args) {
  return args.filter(arg => !/^-Dai\.selfplay\.(seed|record\.label|record\.question)=/.test(arg)).join(" ");
}

export function estimateMs(job, jobs) {
  if (job.estimateHours) return job.estimateHours * HOUR;
  const past = jobs
    .filter(other => other.status === "done" && signature(other.args) === signature(job.args))
    .map(other => Date.parse(other.finishedAt) - Date.parse(other.startedAt))
    .sort((a, b) => a - b);
  return past.length ? past[Math.floor(past.length / 2)] : null;
}

// When each running and queued job should start and finish, queued jobs in the order they were approved. A job
// without an estimate leaves everything behind it unknown.
export function schedule(jobs, now = Date.now()) {
  const times = new Map();
  let cursor = now;
  for (const job of jobs.filter(j => j.status === "running")) {
    const estimate = estimateMs(job, jobs);
    const finish = estimate == null ? null : Math.max(Date.parse(job.startedAt) + estimate, now + 60_000);
    times.set(job.id, { start: Date.parse(job.startedAt), finish });
    cursor = finish == null ? null : Math.max(cursor ?? 0, finish);
  }
  const queued = jobs.filter(j => j.status === "queued").sort((a, b) => a.approvedAt.localeCompare(b.approvedAt));
  for (const job of queued) {
    const estimate = estimateMs(job, jobs);
    const finish = cursor == null || estimate == null ? null : cursor + estimate;
    times.set(job.id, { start: cursor, finish });
    cursor = finish;
  }
  return times;
}

function clock(ms) {
  if (ms == null) return "?";
  const date = new Date(ms);
  const pad = n => String(n).padStart(2, "0");
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const hoursBetween = (from, to) => (from && to ? ((Date.parse(to) - Date.parse(from)) / HOUR).toFixed(2) : null);

export function listing(queue, all = false) {
  const jobs = readJobs(queue);
  const times = schedule(jobs);
  const runner = lockHolder(queue);
  const lines = [runner ? `Runner: pid ${runner.pid} since ${clock(Date.parse(runner.startedAt))}` : "Runner: idle"];
  const finished = jobs.filter(job => FINISHED.has(job.status));
  const shown = [...jobs.filter(job => !FINISHED.has(job.status)), ...(all ? finished : finished.slice(-5))];
  for (const job of shown) {
    const time = times.get(job.id);
    let when = "";
    if (job.status === "awaiting-approval") when = "needs the user's yes";
    else if (time) when = `start ${clock(time.start)}, finish ${clock(time.finish)}`;
    else if (job.finishedAt) when = `waited ${hoursBetween(job.approvedAt, job.startedAt) ?? "-"} h, ran ${hoursBetween(job.startedAt, job.finishedAt) ?? "-"} h`;
    const estimate = estimateMs(job, jobs);
    lines.push(
      [
        job.id.padEnd(36),
        job.status.padEnd(17),
        job.commit.slice(0, 10),
        `est ${estimate == null ? "?" : (estimate / HOUR).toFixed(1) + " h"}`.padEnd(10),
        when,
        job.cancelRequested && job.status === "running" ? "(stopping)" : "",
        job.submittedBy ? `by ${job.submittedBy}` : "",
      ]
        .filter(Boolean)
        .join("  "),
    );
  }
  return lines.join("\n");
}

function isAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return error.code === "EPERM";
  }
}

const lockFile = queue => path.join(queue, "runner.lock");

function lockHolder(queue) {
  try {
    const holder = JSON.parse(fs.readFileSync(lockFile(queue), "utf8"));
    return isAlive(holder.pid) ? holder : null;
  } catch {
    return null;
  }
}

function acquireLock(queue) {
  fs.mkdirSync(queue, { recursive: true });
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const fd = fs.openSync(lockFile(queue), "wx");
      fs.writeSync(fd, JSON.stringify({ pid: process.pid, startedAt: new Date().toISOString() }));
      fs.closeSync(fd);
      return true;
    } catch (error) {
      if (error.code !== "EEXIST" || lockHolder(queue)) return false;
      fs.rmSync(lockFile(queue), { force: true });
    }
  }
  return false;
}

function releaseLock(queue) {
  const holder = (() => {
    try {
      return JSON.parse(fs.readFileSync(lockFile(queue), "utf8"));
    } catch {
      return null;
    }
  })();
  if (holder?.pid === process.pid) fs.rmSync(lockFile(queue), { force: true });
}

function ensureRunner(queue) {
  if (process.env.AI_AB_QUEUE_NO_AUTOSTART === "1" || lockHolder(queue)) return;
  fs.mkdirSync(queue, { recursive: true });
  const log = fs.openSync(path.join(queue, "runner.log"), "a");
  spawn(process.execPath, [SCRIPT, "run"], {
    detached: true,
    stdio: ["ignore", log, log],
    windowsHide: true,
    env: { ...process.env, AI_AB_QUEUE_DIR: queue },
  }).unref();
  fs.closeSync(log);
}

function quote(arg) {
  return `"${String(arg).replace(/"/g, "'")}"`;
}

function recordDir(job) {
  const given = job.args.find(arg => arg.startsWith("-Dai.selfplay.record.dir="));
  return given ? path.resolve(job.worktree, given.split("=").slice(1).join("=")) : path.join(job.worktree, "docs", "ai", "experiments");
}

export function mavenArgs(job) {
  const args = [...job.args];
  if (!args.some(arg => !arg.startsWith("-"))) args.unshift("-o", "test");
  const has = key => args.some(arg => arg.startsWith(`-D${key}=`));
  if (!has("ai.selfplay.record.dir")) args.push(`-Dai.selfplay.record.dir=${recordDir(job)}`);
  if (!has("ai.selfplay.record.label")) args.push(`-Dai.selfplay.record.label=${job.label}`);
  if (job.question && !has("ai.selfplay.record.question")) args.push(`-Dai.selfplay.record.question=${job.question}`);
  args.push(`-D${QUEUE_PROPERTY}=${job.id}`);
  return args;
}

const normalized = text => (text ?? "").replace(/\\/g, "/").toLowerCase();

export function javaProcesses() {
  if (process.platform !== "win32") return null;
  const script =
    `$p = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='cmd.exe'" | ` +
    `Select-Object Name,ProcessId,ParentProcessId,CommandLine); ` +
    `$c = (Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average; ` +
    `ConvertTo-Json -Depth 3 -Compress @{ cpu = $c; java = $p }`;
  try {
    const text = execFileSync("powershell", ["-NoProfile", "-NonInteractive", "-Command", script], {
      encoding: "utf8",
      windowsHide: true,
      timeout: 60_000,
    });
    const data = JSON.parse(text);
    const java = [].concat(data.java ?? []).map(p => ({
      shell: /^cmd\.exe$/i.test(p.Name ?? ""),
      pid: p.ProcessId,
      parent: p.ParentProcessId,
      commandLine: p.CommandLine ?? "",
    }));
    return { cpu: data.cpu, java };
  } catch {
    return null;
  }
}

const isMaven = process =>
  /plexus-classworlds/i.test(process?.commandLine ?? "") && !/-Didea\.version=/.test(process.commandLine);

const projectFolder = process =>
  normalized(process.commandLine.match(/multiModuleProjectDirectory=("?)([^"\s]+)\1/)?.[2]) || "unknown";

function hasMavenAncestor(process, byPid) {
  for (let parent = byPid.get(process.parent), hops = 0; parent && hops < 5; parent = byPid.get(parent.parent), hops++) {
    if (isMaven(parent)) return true;
  }
  return false;
}

// Sorts java processes into the job's own (descendants of its pids: surefire forks run from a temp folder through
// cmd.exe, so only the process tree ties them to the job), other Maven builds named by their project folder, and
// anything else. Shell processes only link the tree and are never counted.
export function classify(processes, ownRoots) {
  const own = new Set(ownRoots);
  for (let grew = true; grew; ) {
    grew = false;
    for (const process of processes) {
      if (!own.has(process.pid) && own.has(process.parent)) {
        own.add(process.pid);
        grew = true;
      }
    }
  }
  const byPid = new Map(processes.map(process => [process.pid, process]));
  const result = { own: [], builds: [], other: 0 };
  for (const process of processes.filter(process => !process.shell)) {
    if (own.has(process.pid)) result.own.push(process.pid);
    else if (isMaven(process)) result.builds.push(projectFolder(process));
    else if (/surefirebooter/i.test(process.commandLine)) {
      if (!hasMavenAncestor(process, byPid)) result.builds.push("surefire without its Maven");
    } else result.other++;
  }
  return result;
}

class LoadSampler {
  constructor(roots) {
    this.ownPids = new Set(roots);
    this.samples = 0;
    this.cpu = [];
    this.withOtherBuilds = 0;
    this.otherBuilds = new Set();
    this.maxOtherJava = 0;
  }

  sample() {
    const found = javaProcesses();
    if (!found) return;
    const sorted = classify(found.java, this.ownPids);
    sorted.own.forEach(pid => this.ownPids.add(pid));
    this.samples++;
    if (typeof found.cpu === "number") this.cpu.push(found.cpu);
    if (sorted.builds.length) this.withOtherBuilds++;
    sorted.builds.forEach(where => this.otherBuilds.add(where));
    this.maxOtherJava = Math.max(this.maxOtherJava, sorted.other);
  }

  summary() {
    const mean = this.cpu.length ? this.cpu.reduce((a, b) => a + b, 0) / this.cpu.length : null;
    return {
      samples: this.samples,
      cpuPercentAverage: mean == null ? null : Math.round(mean),
      cpuPercentMax: this.cpu.length ? Math.max(...this.cpu) : null,
      samplesWithOtherBuilds: this.withOtherBuilds,
      otherBuildWorktrees: [...this.otherBuilds].sort(),
      otherJavaProcessesMax: this.maxOtherJava,
    };
  }
}

function killTree(pid) {
  if (!pid || !isAlive(pid)) return;
  if (process.platform === "win32") spawnSync("taskkill", ["/PID", String(pid), "/T", "/F"], { windowsHide: true });
  else {
    try {
      process.kill(-pid, "SIGKILL");
    } catch {
      process.kill(pid, "SIGKILL");
    }
  }
}

// A stopped runner does not stop its Maven child, so the job's java processes (every pid seen in its tree) are
// killed rather than trusted to have exited.
function killLeftovers(pids) {
  const found = javaProcesses();
  if (!found) return;
  for (const pid of classify(found.java, pids ?? []).own) killTree(pid);
}

function frozenPath(job) {
  return path.join(path.dirname(commonDir(job.worktree)), ".claude", "worktrees", `ab-run-${job.id}`);
}

function removeWorktree(job, frozen) {
  try {
    git(job.worktree, "worktree", "remove", "--force", frozen);
  } catch {
    fs.rmSync(frozen, { recursive: true, force: true });
    try {
      git(job.worktree, "worktree", "prune");
    } catch {}
  }
}

function findKey(value, key) {
  if (!value || typeof value !== "object") return undefined;
  if (key in value) return value[key];
  for (const child of Object.values(value)) {
    const found = findKey(child, key);
    if (found !== undefined) return found;
  }
  return undefined;
}

export function recordFolders(job) {
  const runs = path.join(recordDir(job), "runs");
  if (!fs.existsSync(runs)) return [];
  return fs
    .readdirSync(runs, { withFileTypes: true })
    .filter(entry => entry.isDirectory())
    .map(entry => path.join(runs, entry.name))
    .filter(folder => {
      try {
        return findKey(JSON.parse(fs.readFileSync(path.join(folder, "run.json"), "utf8")), QUEUE_PROPERTY) === job.id;
      } catch {
        return false;
      }
    });
}

function queueSection(job) {
  return {
    id: job.id,
    submittedBy: job.submittedBy || null,
    sourceWorktree: job.worktree,
    sourceBranch: job.branch,
    submittedAt: job.submittedAt,
    approvedAt: job.approvedAt,
    startedAt: job.startedAt,
    finishedAt: job.finishedAt,
    waitHours: Number(hoursBetween(job.approvedAt, job.startedAt)),
    runHours: Number(hoursBetween(job.startedAt, job.finishedAt)),
    estimateHours: job.estimateHours,
    exitCode: job.exitCode,
    status: job.status,
    load: job.load,
    mavenArguments: job.mavenArguments,
  };
}

function annotate(folder, job) {
  const runJson = path.join(folder, "run.json");
  const run = JSON.parse(fs.readFileSync(runJson, "utf8"));
  run.queue = queueSection(job);
  writeJson(runJson, run);
  const readme = path.join(folder, "README.md");
  if (!fs.existsSync(readme)) return;
  const load = job.load ?? {};
  const others = load.otherBuildWorktrees?.length ? load.otherBuildWorktrees.join(", ") : "none";
  fs.appendFileSync(
    readme,
    `\n## Queue\n\n` +
      `- Job \`${job.id}\`${job.submittedBy ? ` from ${job.submittedBy}` : ""}, ${job.status} (exit ${job.exitCode})\n` +
      `- Waited ${run.queue.waitHours} h after approval, ran ${run.queue.runHours} h including the build` +
      `${job.estimateHours ? ` (estimate ${job.estimateHours} h)` : ""}\n` +
      `- Load over ${load.samples ?? 0} samples: CPU ${load.cpuPercentAverage ?? "?"}% average, ` +
      `${load.cpuPercentMax ?? "?"}% max; other Maven builds in ${load.samplesWithOtherBuilds ?? 0} samples (${others})\n`,
  );
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

async function runJob(queue, queued, { sampleSeconds, pollSeconds }) {
  const frozen = frozenPath(queued);
  let job = updateJob(queue, queued.id, job => {
    job.status = "running";
    job.startedAt = new Date().toISOString();
    job.runnerPid = process.pid;
    job.frozenWorktree = frozen;
  });
  const log = path.join(queue, "logs", `${job.id}.log`);
  fs.mkdirSync(path.dirname(log), { recursive: true });
  let sampler = new LoadSampler([]);
  let exitCode;
  try {
    if (fs.existsSync(frozen)) removeWorktree(job, frozen);
    git(job.worktree, "worktree", "add", "--detach", frozen, job.commit);
    const args = mavenArgs(job);
    const command = [process.env.AI_AB_QUEUE_MVN ?? "mvn", ...args.map(quote)].join(" ");
    const out = fs.openSync(log, "a");
    fs.writeSync(out, `${new Date().toISOString()} ${command}\n`);
    const child = spawn(command, { cwd: frozen, shell: true, stdio: ["ignore", out, out], windowsHide: true });
    sampler = new LoadSampler([child.pid]);
    job = updateJob(queue, job.id, job => {
      job.childPid = child.pid;
      job.ownPids = [child.pid];
      job.mavenArguments = args;
    });
    const sample = () => {
      sampler.sample();
      updateJob(queue, job.id, job => (job.ownPids = [...sampler.ownPids]));
    };
    const timers = [
      setInterval(() => {
        if (readJob(queue, job.id).cancelRequested) killTree(child.pid);
      }, pollSeconds * 1000),
    ];
    if (sampleSeconds > 0) {
      timers.push(setTimeout(sample, 20_000));
      timers.push(setInterval(sample, sampleSeconds * 1000));
    }
    exitCode = await new Promise(resolve => {
      child.on("exit", code => resolve(code ?? -1));
      child.on("error", () => resolve(-1));
    });
    timers.forEach(clearTimeout);
    fs.closeSync(out);
  } catch (error) {
    fs.appendFileSync(log, `runner error: ${error.stack ?? error}\n`);
    exitCode = -1;
  }
  killLeftovers([...sampler.ownPids]);
  job = updateJob(queue, job.id, job => {
    job.status = job.cancelRequested ? "cancelled" : exitCode === 0 ? "done" : "failed";
    job.finishedAt = new Date().toISOString();
    job.exitCode = exitCode;
    job.load = sampler.summary();
    job.ownPids = [...sampler.ownPids];
  });
  const folders = recordFolders(job);
  for (const folder of folders) {
    try {
      annotate(folder, job);
    } catch (error) {
      fs.appendFileSync(log, `could not add the queue section to ${folder}: ${error}\n`);
    }
  }
  removeWorktree(job, frozen);
  return updateJob(queue, job.id, job => {
    job.records = folders;
    job.log = log;
  });
}

function recoverInterrupted(queue) {
  for (const job of readJobs(queue).filter(j => j.status === "running")) {
    killLeftovers(job.ownPids);
    if (job.frozenWorktree) removeWorktree(job, job.frozenWorktree);
    updateJob(queue, job.id, job => {
      job.status = "interrupted";
      job.finishedAt = new Date().toISOString();
    });
  }
}

export async function runQueue(queue, options = {}) {
  const sampleSeconds = options.sampleSeconds ?? Number(process.env.AI_AB_QUEUE_SAMPLE_SECONDS ?? 300);
  const pollSeconds = options.pollSeconds ?? 30;
  if (!acquireLock(queue)) return false;
  try {
    recoverInterrupted(queue);
    for (;;) {
      const next = readJobs(queue)
        .filter(job => job.status === "queued")
        .sort((a, b) => a.approvedAt.localeCompare(b.approvedAt))[0];
      if (!next) break;
      console.log(`${new Date().toISOString()} start ${next.id}`);
      const job = await runJob(queue, next, { sampleSeconds, pollSeconds });
      console.log(`${new Date().toISOString()} ${job.status} ${job.id} (exit ${job.exitCode})`);
    }
  } finally {
    releaseLock(queue);
  }
  return true;
}

function describe(job) {
  return [
    `${job.id}: ${job.status}${job.exitCode != null ? ` (exit ${job.exitCode})` : ""}`,
    `  commit ${job.commit.slice(0, 10)} on ${job.branch}: ${job.commitSubject}`,
    job.approvedAt && job.startedAt ? `  waited ${hoursBetween(job.approvedAt, job.startedAt)} h` : "",
    job.startedAt && job.finishedAt ? `  ran ${hoursBetween(job.startedAt, job.finishedAt)} h` : "",
    ...(job.records ?? []).map(folder => `  record ${folder}`),
    job.log ? `  log ${job.log}` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

function parse(argv) {
  const split = argv.indexOf("--");
  const own = split < 0 ? argv : argv.slice(0, split);
  const options = { args: split < 0 ? [] : argv.slice(split + 1), positional: [] };
  for (let i = 0; i < own.length; i++) {
    const arg = own[i];
    if (arg === "--approved" || arg === "--all") options[arg.slice(2)] = true;
    else if (arg.startsWith("--")) options[arg.slice(2)] = own[++i];
    else options.positional.push(arg);
  }
  return options;
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  const options = parse(rest);
  const queue = queueDir();
  const id = () => resolveId(queue, options.positional[0] ?? "");
  switch (command) {
    case "submit": {
      const job = submit(process.cwd(), {
        label: options.label,
        question: options.question ?? "",
        hours: options.hours ? Number(options.hours) : null,
        approved: Boolean(options.approved),
        by: options.by ?? "",
        args: options.args,
      });
      console.log(`${job.id} ${job.status}`);
      console.log(listing(queue));
      return 0;
    }
    case "approve":
      console.log(`${approve(queue, id()).id} queued`);
      console.log(listing(queue));
      return 0;
    case "cancel": {
      const job = cancel(queue, id());
      console.log(`${job.id} ${job.cancelRequested ? "stopping" : job.status}`);
      return 0;
    }
    case "list":
      console.log(listing(queue, Boolean(options.all)));
      return 0;
    case "show":
      console.log(JSON.stringify(readJob(queue, id()), null, 2));
      return 0;
    case "wait": {
      const target = id();
      for (;;) {
        const job = readJob(queue, target);
        if (FINISHED.has(job.status)) {
          console.log(describe(job));
          return job.status === "done" ? 0 : 1;
        }
        await sleep(Number(options.poll ?? 60) * 1000);
      }
    }
    case "run":
      if (!(await runQueue(queue))) console.log("Another runner is already working this queue.");
      return 0;
    default:
      console.log(fs.readFileSync(SCRIPT, "utf8").split("\nimport ")[0].replace(/^#!.*\n/, "").replace(/^\/\/ ?/gm, ""));
      return command ? 1 : 0;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === SCRIPT) {
  main().then(
    code => process.exit(code),
    error => {
      console.error(error.message);
      process.exit(1);
    },
  );
}
