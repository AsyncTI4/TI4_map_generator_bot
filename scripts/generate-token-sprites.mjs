#!/usr/bin/env node
import {
  access,
  mkdir,
  readdir,
  readFile,
  rm,
  writeFile,
} from "node:fs/promises";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { execFile } from "node:child_process";
import { cpus } from "node:os";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const scriptDir = dirname(fileURLToPath(import.meta.url));
const botRoot = join(scriptDir, "..");
const resourcesDir = join(botRoot, "src", "main", "resources");
const defaultOutDir = join(resourcesDir, "tokens", "sheets");
const cdnBase = "https://images.asyncti4.com/tokens/sheets";
const officialSources = new Set([
  "base",
  "pok",
  "te",
  "thunders_edge",
  "codex",
  "codex1",
  "codex2",
  "codex3",
  "codex4",
  "tf",
  "twilight_fall",
]);
const officialTokenIds = new Set(["speaker", "tyrant"]);

const args = parseArgs(process.argv.slice(2));
const outDir = args.out ?? defaultOutDir;
const maxWidth = Number(args.maxWidth ?? 1024);
const padding = Number(args.padding ?? 2);
const format = args.format ?? "webp";
const shouldClean = args.clean !== "false";
const concurrency = Number(args.concurrency ?? Math.min(cpus().length, 8));

if (!Number.isFinite(maxWidth) || maxWidth <= 0) {
  throw new Error(`Invalid --max-width value: ${args.maxWidth}`);
}

if (!Number.isFinite(padding) || padding < 0) {
  throw new Error(`Invalid --padding value: ${args.padding}`);
}

if (!["png", "webp"].includes(format)) {
  throw new Error(`Unsupported --format value: ${format}`);
}

if (!Number.isFinite(concurrency) || concurrency <= 0) {
  throw new Error(`Invalid --concurrency value: ${args.concurrency}`);
}

await assertMagickAvailable();

if (shouldClean) {
  await rm(outDir, { recursive: true, force: true });
}
await mkdir(outDir, { recursive: true });

const groups = [
  {
    kind: "token",
    sheet: "tokens",
    dataDir: join(resourcesDir, "data", "tokens"),
    imageDir: join(resourcesDir, "tokens"),
  },
  {
    kind: "attachment",
    sheet: "attachment-tokens",
    dataDir: join(resourcesDir, "data", "attachments"),
    imageDir: join(resourcesDir, "attachment_token"),
  },
];

const manifest = {};
const css = [
  "/* Static CSS matching TI4_map_generator_bot/scripts/generate-token-sprites.mjs output. */",
  ".token-sprite {",
  "  display: inline-block;",
  "  flex: 0 0 auto;",
  "  background-repeat: no-repeat;",
  "  background-size: var(--token-sprite-sheet-width) var(--token-sprite-sheet-height);",
  "}",
  "",
];
const sheets = [];
let frameCount = 0;

for (const group of groups) {
  const entries = await readImageEntries(group);
  const entriesWithSize = await identifyEntries(entries);
  const sheetGroups = splitSheetGroups(group, entriesWithSize);
  manifest[group.kind] = {};

  for (const sheetGroup of sheetGroups) {
    if (sheetGroup.entries.length === 0) continue;

    const template = buildTemplate(sheetGroup.entries, maxWidth, padding);
    const packed = buildSheet(sheetGroup.entries, template);
    const imageName = `${sheetGroup.sheet}.${format}`;

    sheets.push({
      packed,
      imagePath: join(outDir, imageName),
    });

    css.push(
      `.token-sprite--${sheetGroup.sheet} {`,
      `  --token-sprite-sheet-width: ${packed.width}px;`,
      `  --token-sprite-sheet-height: ${packed.height}px;`,
      `  background-image: url("${cdnBase}/${imageName}?v=4");`,
      "}",
      "",
    );

    const framesByImagePath = new Map(
      packed.frames.map((frame) => [frame.imagePath, frame]),
    );

    for (const entry of sheetGroup.entries) {
      const frame = framesByImagePath.get(entry.imagePath);
      if (!frame) {
        throw new Error(`Missing sprite frame for ${entry.imagePath}`);
      }

      const frameClassName = `token-sprite--${group.kind}-${sanitizeCssIdent(entry.id)}`;
      manifest[group.kind][entry.id] = `token-sprite--${sheetGroup.sheet}`;

      css.push(
        `.${frameClassName} {`,
        `  width: ${frame.width}px;`,
        `  height: ${frame.height}px;`,
        `  aspect-ratio: ${frame.width} / ${frame.height};`,
        `  background-position: -${frame.x}px -${frame.y}px;`,
        "}",
        "",
      );
    }

    frameCount += packed.frames.length;
  }
}

await mapLimit(sheets, concurrency, ({ packed, imagePath }) =>
  composeSheet(packed, imagePath, format),
);

await writeFile(join(outDir, "tokenSprites.css"), `${css.join("\n").trimEnd()}\n`);
await writeFile(
  join(outDir, "tokenSprites.ts"),
  buildManifestTs(manifest),
);
await writeFile(
  join(outDir, "tokenSprites.json"),
  `${JSON.stringify(manifest, null, 2)}\n`,
);

console.log(
  `Generated ${sheets.length} token sprite sheets with ${frameCount} frames in ${relative(botRoot, outDir)}`,
);

function parseArgs(rawArgs) {
  const parsed = {};

  for (let index = 0; index < rawArgs.length; index += 1) {
    const arg = rawArgs[index];
    if (!arg.startsWith("--")) continue;

    const [key, inlineValue] = arg.slice(2).split("=", 2);
    const normalizedKey = key.replace(/-([a-z])/g, (_, char) =>
      char.toUpperCase(),
    );
    const value =
      inlineValue ?? (rawArgs[index + 1]?.startsWith("--") ? "true" : rawArgs[++index]);
    parsed[normalizedKey] = value ?? "true";
  }

  return parsed;
}

async function assertMagickAvailable() {
  try {
    await execFileAsync("magick", ["-version"]);
  } catch {
    throw new Error("ImageMagick is required. Install it so `magick` is on PATH.");
  }
}

async function readImageEntries(group) {
  const files = (await readdir(group.dataDir)).filter((file) =>
    file.endsWith(".json"),
  );
  const entries = [];
  const seen = new Set();
  const missing = [];

  for (const file of files.sort()) {
    const data = JSON.parse(await readFile(join(group.dataDir, file), "utf8"));

    for (const item of data) {
      if (!item.id || !item.imagePath) continue;

      const key = `${group.kind}:${item.id}`;
      if (seen.has(key)) continue;
      seen.add(key);

      const imagePath = join(group.imageDir, item.imagePath);
      if (!(await fileExists(imagePath))) {
        missing.push(`${item.id}:${item.imagePath}`);
        continue;
      }

      entries.push({
        id: item.id,
        imagePath,
        source: String(item.source ?? "").trim().toLowerCase(),
      });
    }
  }

  if (missing.length > 0) {
    console.warn(
      `Skipped ${missing.length} ${group.kind} sprite entries with missing images: ${missing.join(", ")}`,
    );
  }

  return entries.sort((a, b) => a.id.localeCompare(b.id));
}

async function fileExists(path) {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

async function identifyEntries(entries) {
  const uniqueImagePaths = [...new Set(entries.map((entry) => entry.imagePath))];
  const { stdout } = await execFileAsync("magick", [
    "identify",
    "-format",
    "%f\t%w\t%h\n",
    ...uniqueImagePaths,
  ]);
  const dimensions = new Map();

  for (const line of stdout.trim().split("\n")) {
    const [file, widthValue, heightValue] = line.split("\t");
    const width = Number(widthValue);
    const height = Number(heightValue);

    if (!file || !Number.isFinite(width) || !Number.isFinite(height)) {
      throw new Error(`Could not parse ImageMagick identify output: ${line}`);
    }

    dimensions.set(file, { width, height });
  }

  return entries.map((entry) => {
    const file = entry.imagePath.split("/").at(-1);
    const size = dimensions.get(file);
    if (!size) {
      throw new Error(`Missing dimensions for ${entry.imagePath}`);
    }
    return {
      ...entry,
      ...size,
    };
  });
}

function buildTemplate(entries, requestedMaxWidth, gap) {
  const slotsByImagePath = new Map();

  for (const entry of entries) {
    if (slotsByImagePath.has(entry.imagePath)) continue;
    slotsByImagePath.set(entry.imagePath, {
      imagePath: entry.imagePath,
      width: entry.width,
      height: entry.height,
    });
  }

  const sorted = [...slotsByImagePath.values()].sort((a, b) => {
    if (b.height !== a.height) return b.height - a.height;
    if (b.width !== a.width) return b.width - a.width;
    return a.imagePath.localeCompare(b.imagePath);
  });

  const widestFrame = Math.max(...sorted.map((entry) => entry.width));
  const maxSheetWidth = Math.max(requestedMaxWidth, widestFrame);
  const frames = [];
  let x = 0;
  let y = 0;
  let rowHeight = 0;
  let sheetWidth = 0;

  for (const entry of sorted) {
    if (x > 0 && x + entry.width > maxSheetWidth) {
      x = 0;
      y += rowHeight + gap;
      rowHeight = 0;
    }

    frames.push({
      ...entry,
      x,
      y,
    });

    x += entry.width + gap;
    rowHeight = Math.max(rowHeight, entry.height);
    sheetWidth = Math.max(sheetWidth, x - gap);
  }

  return {
    width: sheetWidth,
    height: y + rowHeight,
    frames,
  };
}

function splitSheetGroups(group, entries) {
  const spriteEntries =
    group.kind === "token"
      ? entries.filter((entry) => entry.id !== "dmz_large")
      : entries;
  const officialEntries = spriteEntries.filter((entry) =>
    isOfficialEntry(group, entry),
  );
  const homebrewEntries = spriteEntries.filter(
    (entry) => !isOfficialEntry(group, entry),
  );

  return [
    { sheet: `${group.sheet}-official`, entries: officialEntries },
    { sheet: `${group.sheet}-homebrew`, entries: homebrewEntries },
  ];
}

function isOfficialEntry(group, entry) {
  return (
    officialSources.has(entry.source) ||
    (group.kind === "token" && officialTokenIds.has(entry.id))
  );
}

function buildSheet(entries, template) {
  const entriesByImagePath = new Map(
    entries.map((entry) => [entry.imagePath, entry]),
  );

  return {
    width: template.width,
    height: template.height,
    frames: template.frames.map((slot) => {
      const entry = entriesByImagePath.get(slot.imagePath);
      if (!entry) {
        throw new Error(`Missing entry for ${slot.imagePath}`);
      }

      return {
        ...entry,
        x: slot.x,
        y: slot.y,
        width: slot.width,
        height: slot.height,
      };
    }),
  };
}

function sanitizeCssIdent(value) {
  return String(value)
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, "-")
    .replace(/^-+/, "");
}

function buildManifestTs(manifest) {
  return `// Static manifest matching TI4_map_generator_bot/scripts/generate-token-sprites.mjs output.
export type TokenSpriteKind = "token" | "attachment";

export type TokenSprite = {
  kind: TokenSpriteKind;
  id: string;
  sheetClassName: string;
};

const TOKEN_SPRITE_SHEETS: Record<TokenSpriteKind, Record<string, string>> = ${JSON.stringify(manifest, null, 2)};

export function getTokenSprite(
  kind: TokenSpriteKind,
  id: string
): TokenSprite | undefined {
  const sheetClassName = TOKEN_SPRITE_SHEETS[kind][id];
  if (!sheetClassName) return undefined;

  return { kind, id, sheetClassName };
}
`;
}

async function composeSheet(sheet, outputPath, outputFormat) {
  const args = ["-size", `${sheet.width}x${sheet.height}`, "canvas:none"];

  for (const frame of sheet.frames) {
    args.push(frame.imagePath, "-geometry", `+${frame.x}+${frame.y}`, "-composite");
  }

  if (outputFormat === "webp") {
    args.push("-define", "webp:lossless=true");
  }

  args.push(outputPath);
  await execFileAsync("magick", args, { maxBuffer: 1024 * 1024 * 10 });
}

async function mapLimit(items, limit, callback) {
  let nextIndex = 0;
  const workers = Array.from(
    { length: Math.min(limit, items.length) },
    async () => {
      while (nextIndex < items.length) {
        const item = items[nextIndex++];
        await callback(item);
      }
    },
  );

  await Promise.all(workers);
}
