import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const [statsText, repoRoot, out] = process.argv.slice(2);
if (!statsText || !repoRoot || !out) {
  console.error("usage: node scripts/ai-action-card-stats.mjs <stats.txt> <repo root> <out.json>");
  process.exit(1);
}

const DECK = "action_cards_te";
const data = join(repoRoot, "src/main/resources/data");
const deck = readdirSync(join(data, "decks"))
  .flatMap(file => JSON.parse(readFileSync(join(data, "decks", file), "utf8")))
  .find(entry => entry.alias === DECK);
const names = {};
for (const file of readdirSync(join(data, "action_cards"))) {
  for (const card of JSON.parse(readFileSync(join(data, "action_cards", file), "utf8"))) names[card.alias] = card.name;
}
const copies = {};
for (const id of deck.cardIDs) copies[names[id]] = (copies[names[id]] ?? 0) + 1;

const lines = readFileSync(statsText, "utf8")
  .split(/\r?\n/)
  .map(line => line.replace(/[*_`]+(?=\S)|(?<=\S)[*_`]+(?=:|$)/g, "").replace(/^#+\s*/, "").trim());
const percent = text => Math.round(parseFloat(text) * 10) / 1000;
const cards = new Map();
for (let i = 0; i < lines.length - 5; i++) {
  if (!lines[i].endsWith(":") || !copies[lines[i].slice(0, -1)]) continue;
  const impact = lines[i + 1].match(/^([\d.]+)(\*?) Impact Score$/);
  const win = lines[i + 2].match(/^([\d.]+)% win/);
  const play = lines[i + 3].match(/^([\d.]+)% play/);
  const cancel = lines[i + 4].match(/^([\d.]+)% cancel/);
  const counts = lines[i + 5].match(/^(\d+) wins?, (\d+) plays?, (\d+) cancels?$/);
  if (!impact || !win || !play || !cancel || !counts) continue;
  const name = lines[i].slice(0, -1);
  const oneOf = copies[name] === 1;
  cards.set(name, {
    name, copies: copies[name], impact: parseFloat(impact[1]), thin: impact[2] === "*",
    winRate: percent(win[1]), playRate: percent(play[1]), cancelRate: percent(cancel[1]),
    wins: +counts[1], plays: +counts[2], cancels: +counts[3],
    replays: oneOf ? 0 : null, gamesReplayed: oneOf ? 0 : null,
    firstPlayWins: oneOf ? 0 : null, firstPlaysUncanceled: oneOf ? 0 : null,
    replayWins: oneOf ? 0 : null, replaysUncanceled: oneOf ? 0 : null,
  });
}

const replay = /^(.+?): (\d+) replays? \([\d.]+% of (\d+) plays\), replayed in [\d.]+% of games \((\d+)\/(\d+)\); first plays won (?:[\d.]+%|-) \((\d+)\/(\d+)\), replays won (?:[\d.]+%|-) \((\d+)\/(\d+)\)$/;
let replayGames = null;
for (const line of lines) {
  const match = line.match(replay);
  if (!match || match[1] === "All 1-ofs") continue;
  const card = cards.get(match[1]);
  if (!card) throw new Error(`replay row for a card with no Impact Score block: ${match[1]}`);
  if (+match[3] !== card.plays) throw new Error(`play count mismatch for ${match[1]}`);
  Object.assign(card, {
    replays: +match[2], gamesReplayed: +match[4], firstPlayWins: +match[6],
    firstPlaysUncanceled: +match[7], replayWins: +match[8], replaysUncanceled: +match[9],
  });
  replayGames = +match[5];
}

const average = {};
const averageStart = lines.indexOf("Average across all cards:");
for (const line of lines.slice(averageStart + 1, averageStart + 5)) {
  const value = parseFloat(line);
  if (line.endsWith("Impact Score")) average.impact = value;
  else if (line.includes("win rate")) average.winRate = percent(value);
  else if (line.includes("draw rate")) average.playRate = percent(value);
  else if (line.includes("cancel rate")) average.cancelRate = percent(value);
}
const plays = [...cards.values()].reduce((sum, card) => sum + card.plays, 0);
const cancels = [...cards.values()].reduce((sum, card) => sum + card.cancels, 0);
average.cancelRateWeighted = Math.round((cancels / plays) * 10000) / 10000;
average.baselineWinRate = Math.round((1 / 6) * 10000) / 10000;

const draws = lines.find(line => line.startsWith("Estimated draws:"))?.match(/(\d+) 1-ofs/);
const overruleTargets = {};
const targetStart = lines.indexOf("Overrule targets");
for (const line of lines.slice(targetStart + 1)) {
  const match = line.match(/^(\w+): (\d+) \(/);
  if (match) overruleTargets[match[1]] = +match[2];
  else if (Object.keys(overruleTargets).length) break;
}

const missing = Object.keys(copies).filter(name => !cards.has(name));
if (missing.length) throw new Error(`no statistics for: ${missing.join(", ")}`);
writeFileSync(out, JSON.stringify({
  deck: DECK,
  sample: [lines[0], lines.find(line => line.startsWith("Only games started"))].filter(Boolean).join(" "),
  estimatedDrawsPerCopy: draws ? +draws[1] : null,
  replayGames,
  average,
  overruleTargets,
  cards: [...cards.values()],
}, null, 2) + "\n");
console.log(`${cards.size} cards, ${Object.keys(overruleTargets).length} Overrule targets, weighted cancel rate ${average.cancelRateWeighted}`);
