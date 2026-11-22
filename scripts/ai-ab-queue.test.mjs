// node --test scripts/ai-ab-queue.test.mjs
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import {
  approve,
  cancel,
  classify,
  mavenArgs,
  readJob,
  readJobs,
  resolveId,
  runQueue,
  schedule,
  submit,
} from "./ai-ab-queue.mjs";

const HOUR = 3_600_000;

// A throwaway repository with a fake "mvn" that writes a catalog run the way AbRecord does.
function sandbox() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "ab-queue-"));
  const repo = path.join(root, "repo");
  fs.mkdirSync(path.join(repo, "src"), { recursive: true });
  fs.writeFileSync(path.join(repo, "pom.xml"), "<project/>\n");
  fs.writeFileSync(path.join(repo, "src", "Code.txt"), "v1\n");
  const git = (...args) => execFileSync("git", ["-C", repo, ...args], { encoding: "utf8" });
  git("init", "-q");
  git("-c", "user.email=t@t", "-c", "user.name=t", "add", ".");
  git("-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "first");
  const fake = path.join(root, "fake-mvn.mjs");
  fs.writeFileSync(
    fake,
    `import fs from "node:fs"; import path from "node:path";
const props = Object.fromEntries(process.argv.slice(2).filter(a => a.startsWith("-D")).map(a => { const i = a.indexOf("="); return [a.slice(2, i), a.slice(i + 1)]; }));
const code = fs.readFileSync(path.join(process.cwd(), "src", "Code.txt"), "utf8").trim();
const folder = path.join(props["ai.selfplay.record.dir"], "runs", "fake-" + props["ai.selfplay.queue.id"]);
fs.mkdirSync(folder, { recursive: true });
fs.writeFileSync(path.join(folder, "run.json"), JSON.stringify({ code, setup: { properties: props } }));
fs.writeFileSync(path.join(folder, "README.md"), "# fake\\n");
setTimeout(() => process.exit(Number(props["fake.exit"] ?? 0)), Number(props["fake.ms"] ?? 50));
`,
  );
  process.env.AI_AB_QUEUE_DIR = path.join(root, "queue");
  process.env.AI_AB_QUEUE_MVN = `"${process.execPath}" "${fake}"`;
  process.env.AI_AB_QUEUE_NO_AUTOSTART = "1";
  return { root, repo, queue: process.env.AI_AB_QUEUE_DIR, git };
}

test("a job runs the committed code from a frozen checkout and its record gets a queue section", async () => {
  const { root, repo, queue, git } = sandbox();
  const job = submit(repo, { label: "Win break", question: "Does it help?", hours: 2, args: ["-Dtest=X#y"] });
  assert.equal(job.status, "awaiting-approval");

  // The user's yes has not come, so the runner leaves the job alone.
  await runQueue(queue, { sampleSeconds: 0, pollSeconds: 1 });
  assert.equal(readJob(queue, job.id).status, "awaiting-approval");

  approve(queue, job.id);
  // Edits after submitting do not reach the run: it plays the commit.
  fs.writeFileSync(path.join(repo, "src", "Code.txt"), "v2 edited\n");
  await runQueue(queue, { sampleSeconds: 0, pollSeconds: 1 });

  const done = readJob(queue, job.id);
  assert.equal(done.status, "done");
  assert.equal(done.records.length, 1);
  const run = JSON.parse(fs.readFileSync(path.join(done.records[0], "run.json"), "utf8"));
  assert.equal(run.code, "v1");
  assert.equal(run.queue.id, job.id);
  assert.equal(run.queue.status, "done");
  assert.equal(typeof run.queue.waitHours, "number");
  assert.equal(run.setup.properties["ai.selfplay.record.label"], "Win break");
  assert.equal(run.setup.properties["ai.selfplay.record.question"], "Does it help?");
  assert.match(fs.readFileSync(path.join(done.records[0], "README.md"), "utf8"), /## Queue/);
  assert.ok(!fs.existsSync(done.frozenWorktree), "the frozen checkout is removed");
  assert.ok(!git("worktree", "list").includes("ab-run-"));
  fs.rmSync(root, { recursive: true, force: true });
});

test("submitting refuses uncommitted code", () => {
  const { root, repo } = sandbox();
  fs.writeFileSync(path.join(repo, "src", "New.txt"), "new\n");
  assert.throws(() => submit(repo, { label: "x", args: ["-Dtest=X"] }), /commit first/);
  fs.rmSync(root, { recursive: true, force: true });
});

test("jobs run one at a time in the order they were approved, and failures are kept", async () => {
  const { root, repo, queue } = sandbox();
  const first = submit(repo, { label: "first", args: ["-Dtest=X", "-Dfake.exit=3"] });
  const second = submit(repo, { label: "second", approved: true, args: ["-Dtest=X"] });
  approve(queue, first.id);
  await runQueue(queue, { sampleSeconds: 0, pollSeconds: 1 });
  const [a, b] = [readJob(queue, first.id), readJob(queue, second.id)];
  assert.equal(b.status, "done");
  assert.equal(a.status, "failed");
  assert.equal(a.exitCode, 3);
  assert.ok(b.finishedAt <= a.startedAt, "the job approved first runs first");
  fs.rmSync(root, { recursive: true, force: true });
});

test("cancelling a waiting job drops it", () => {
  const { root, repo, queue } = sandbox();
  const job = submit(repo, { label: "drop me", args: ["-Dtest=X"] });
  cancel(queue, resolveId(queue, "drop-me"));
  assert.equal(readJob(queue, job.id).status, "cancelled");
  assert.throws(() => approve(queue, job.id), /cancelled/);
  fs.rmSync(root, { recursive: true, force: true });
});

test("the schedule chains estimates and falls back to past runs of the same command", () => {
  const now = Date.parse("2026-11-22T12:00:00Z");
  const at = hours => new Date(now + hours * HOUR).toISOString();
  const jobs = [
    { id: "old", status: "done", args: ["-Dtest=X", "-Dai.selfplay.seed=1"], startedAt: at(-30), finishedAt: at(-27) },
    { id: "run", status: "running", args: ["-Dtest=Y"], estimateHours: 4, startedAt: at(-1), submittedAt: at(-2) },
    { id: "q2", status: "queued", args: ["-Dtest=X", "-Dai.selfplay.seed=2"], approvedAt: at(-0.5) },
    { id: "q3", status: "queued", args: ["-Dtest=Z"], approvedAt: at(-0.2) },
    { id: "q4", status: "queued", args: ["-Dtest=Y"], estimateHours: 1, approvedAt: at(-0.1) },
  ];
  const times = schedule(jobs, now);
  assert.equal(times.get("run").finish, now + 3 * HOUR);
  assert.equal(times.get("q2").start, now + 3 * HOUR);
  assert.equal(times.get("q2").finish, now + 6 * HOUR, "q2 borrows the 3 h of the earlier run with the same command");
  assert.equal(times.get("q3").finish, null, "no estimate and no history");
  assert.equal(times.get("q4").start, null, "unknown once a job ahead has no estimate");
});

test("Maven arguments add the goal, the catalog, the label and the queue id unless given", () => {
  const job = { id: "j1", label: "lbl", question: 'say "hi"', worktree: "/w", args: ["-Dtest=X", "-Dai.selfplay.record.label=mine"] };
  const args = mavenArgs(job);
  assert.deepEqual(args.slice(0, 2), ["-o", "test"]);
  assert.ok(args.includes("-Dai.selfplay.record.label=mine"));
  assert.ok(!args.includes("-Dai.selfplay.record.label=lbl"));
  assert.ok(args.includes("-Dai.selfplay.queue.id=j1"));
  assert.ok(args.some(arg => arg.startsWith("-Dai.selfplay.record.dir=")));
  assert.ok(mavenArgs({ ...job, args: ["test", "-Dtest=X"] })[0] === "test");
});

test("other Maven builds are told apart from the job's own process tree", () => {
  const maven = dir => `java -cp plexus-classworlds-2.8.0.jar -Dmaven.multiModuleProjectDirectory=${dir} org.codehaus`;
  // Surefire forks run from a temp folder, so their command line never names the worktree.
  const surefire = "java -jar C:\\Temp\\surefire1\\surefirebooter-1.jar";
  const sorted = classify(
    [
      { pid: 10, parent: 1, commandLine: maven("C:/repo/.claude/worktrees/ab-run-j1") },
      { pid: 12, parent: 10, shell: true, commandLine: "cmd.exe /X /D /C" },
      { pid: 11, parent: 12, commandLine: surefire },
      { pid: 20, parent: 2, commandLine: maven("C:\\repo\\.claude\\worktrees\\other") },
      { pid: 22, parent: 20, shell: true, commandLine: "cmd.exe /X /D /C" },
      { pid: 21, parent: 22, commandLine: surefire },
      { pid: 31, parent: 999, commandLine: surefire },
      { pid: 40, parent: 4, commandLine: "java -Didea.version=2026.1 -cp plexus-classworlds.jar embedder" },
    ],
    [1],
  );
  // The job's shell is pid 1: its Maven and that Maven's fork (through cmd.exe) are its own; shells are not counted.
  assert.deepEqual(sorted.own, [10, 11]);
  // The other worktree's fork counts with its Maven; a fork whose Maven is gone still counts as a build.
  assert.deepEqual(sorted.builds, ["c:/repo/.claude/worktrees/other", "surefire without its Maven"]);
  // IntelliJ's Maven embedder is not a build.
  assert.equal(sorted.other, 1);
  assert.equal(readJobs(fs.mkdtempSync(path.join(os.tmpdir(), "ab-empty-"))).length, 0);
});

test("cancelling a running job stops its whole process tree", async () => {
  const { root, repo, queue } = sandbox();
  const job = submit(repo, { label: "long", approved: true, args: ["-Dtest=X", "-Dfake.ms=120000"] });
  const running = runQueue(queue, { sampleSeconds: 0, pollSeconds: 1 });
  while (!readJob(queue, job.id).childPid) await new Promise(resolve => setTimeout(resolve, 200));
  const started = Date.now();
  cancel(queue, job.id);
  await running;
  assert.equal(readJob(queue, job.id).status, "cancelled");
  assert.ok(Date.now() - started < 60_000, "the two-minute fake run was stopped");
  fs.rmSync(root, { recursive: true, force: true });
});
