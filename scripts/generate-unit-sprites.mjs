#!/usr/bin/env node
import { mkdir, readdir, readFile, rm } from "node:fs/promises";
import { basename, dirname, extname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { execFile } from "node:child_process";
import { cpus } from "node:os";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const scriptDir = dirname(fileURLToPath(import.meta.url));
const botRoot = join(scriptDir, "..");
const defaultSourceDir = join(
  botRoot,
  "src",
  "main",
  "resources",
  "units",
);
const defaultColorsDir = join(
  botRoot,
  "src",
  "main",
  "resources",
  "data",
  "colors",
);
const defaultOutDir = join(defaultSourceDir, "sheets");
const specialUnitFiles = new Map([
  ["PlenaryOrbital", "plenaryorbital"],
  ["TyrantsLament", "tyrantslament"],
]);
const sourcePrefixAliases = new Map([
  ["galcier", "gcr"],
]);

const args = parseArgs(process.argv.slice(2));
const sourceDir = args.source ?? defaultSourceDir;
const colorsDir = args.colors ?? defaultColorsDir;
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

const colorAliases = await readColorAliases(colorsDir);
const units = await readUnits(sourceDir, colorAliases);
const colors = groupByColor(units);
const sharedUnits = colors.get("shared") ?? [];
const coloredEntries = [...colors.entries()]
  .filter(([color]) => color !== "shared")
  .sort();
const coloredTemplate = buildTemplate(
  coloredEntries.flatMap(([, colorUnits]) => colorUnits),
  maxWidth,
  padding,
);
const sharedTemplate =
  sharedUnits.length > 0 ? buildTemplate(sharedUnits, maxWidth, padding) : null;
let sheetCount = 0;
let frameCount = 0;
const sheets = [];

for (const [color, colorUnits] of coloredEntries) {
  const packed = buildSheet(colorUnits, coloredTemplate);
  const imageName = `${color}.${format}`;
  sheets.push({
    packed,
    imagePath: join(outDir, imageName),
  });

  sheetCount += 1;
  frameCount += packed.frames.length;
}

if (sharedTemplate) {
  const packed = buildSheet(sharedUnits, sharedTemplate);
  const imageName = `shared.${format}`;
  sheets.push({
    packed,
    imagePath: join(outDir, imageName),
  });

  sheetCount += 1;
  frameCount += packed.frames.length;
}

await mapLimit(sheets, concurrency, ({ packed, imagePath }) =>
  composeSheet(packed, imagePath, format),
);

console.log(
  `Generated ${sheetCount} unit sprite sheets with ${frameCount} frames in ${relative(botRoot, outDir)}`,
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

async function readColorAliases(dir) {
  const aliases = new Map();
  const files = (await readdir(dir)).filter((file) => file.endsWith(".json"));

  for (const file of files) {
    const colors = JSON.parse(await readFile(join(dir, file), "utf8"));

    for (const color of colors) {
      const alias = color.alias?.trim();
      if (!alias) continue;

      for (const value of [
        color.alias,
        color.name,
        color.displayName,
        ...(color.aliases ?? []),
      ]) {
        if (value) {
          aliases.set(String(value).trim().toLowerCase(), alias);
        }
      }
    }
  }

  return aliases;
}

async function readUnits(dir, colorAliases) {
  const files = await readdir(dir);
  const imageFiles = files.filter((file) => /\.(png|webp)$/i.test(file));
  const skippedFiles = [];
  const parsedUnits = imageFiles
    .sort()
    .map((file) => {
      const parsed = parseUnitFile(file, colorAliases);
      if (!parsed) {
        skippedFiles.push(file);
        return null;
      }
      return {
        ...parsed,
        file,
        imagePath: join(dir, file),
      };
    })
    .filter(Boolean);

  if (skippedFiles.length > 0) {
    console.warn(
      `Skipped ${skippedFiles.length} non color-prefixed image files: ${skippedFiles.join(", ")}`,
    );
  }

  const dimensions = await identifyImages(parsedUnits.map((unit) => unit.imagePath));

  return parsedUnits.map((unit) => {
    const size = dimensions.get(unit.file);
    if (!size) {
      throw new Error(`Missing dimensions for ${unit.imagePath}`);
    }
    return {
      ...unit,
      ...size,
    };
  });
}

function parseUnitFile(file, colorAliases) {
  const extension = extname(file);
  const name = basename(file, extension).trim();
  const specialUnit = specialUnitFiles.get(name);
  if (specialUnit) {
    return {
      color: "shared",
      unit: specialUnit,
    };
  }

  const separatorIndex = name.indexOf("_");

  if (separatorIndex <= 0 || separatorIndex === name.length - 1) {
    return null;
  }

  const rawColor = name.slice(0, separatorIndex).trim();
  const rawColorKey = rawColor.toLowerCase();
  const color =
    sourcePrefixAliases.get(rawColorKey) ?? colorAliases.get(rawColorKey);

  if (!color) {
    return null;
  }

  return {
    color,
    unit: name.slice(separatorIndex + 1).trim(),
  };
}

async function identifyImages(imagePaths) {
  const { stdout } = await execFileAsync("magick", [
    "identify",
    "-format",
    "%f\t%w\t%h\n",
    ...imagePaths,
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

  return dimensions;
}

function groupByColor(units) {
  const colors = new Map();

  for (const unit of units) {
    if (!colors.has(unit.color)) {
      colors.set(unit.color, []);
    }
    colors.get(unit.color).push(unit);
  }

  return colors;
}

function buildTemplate(units, requestedMaxWidth, gap) {
  const slotsByUnit = new Map();

  for (const unit of units) {
    const slot = slotsByUnit.get(unit.unit);
    if (!slot) {
      slotsByUnit.set(unit.unit, {
        unit: unit.unit,
        width: unit.width,
        height: unit.height,
      });
      continue;
    }

    slot.width = Math.max(slot.width, unit.width);
    slot.height = Math.max(slot.height, unit.height);
  }

  const sorted = [...slotsByUnit.values()].sort((a, b) => {
    if (b.height !== a.height) return b.height - a.height;
    if (b.width !== a.width) return b.width - a.width;
    return a.unit.localeCompare(b.unit);
  });

  const widestFrame = Math.max(...sorted.map((unit) => unit.width));
  const maxSheetWidth = Math.max(requestedMaxWidth, widestFrame);
  const frames = [];
  let x = 0;
  let y = 0;
  let rowHeight = 0;
  let sheetWidth = 0;

  for (const unit of sorted) {
    if (x > 0 && x + unit.width > maxSheetWidth) {
      x = 0;
      y += rowHeight + gap;
      rowHeight = 0;
    }

    frames.push({
      ...unit,
      x,
      y,
    });

    x += unit.width + gap;
    rowHeight = Math.max(rowHeight, unit.height);
    sheetWidth = Math.max(sheetWidth, x - gap);
  }

  const sheetHeight = y + rowHeight;

  return {
    width: sheetWidth,
    height: sheetHeight,
    frames,
  };
}

function buildSheet(units, template) {
  const unitsByType = new Map(units.map((unit) => [unit.unit, unit]));

  return {
    width: template.width,
    height: template.height,
    frames: template.frames.flatMap((slot) => {
      const unit = unitsByType.get(slot.unit);
      if (!unit) return [];

      return {
        ...unit,
        x: slot.x,
        y: slot.y,
        width: slot.width,
        height: slot.height,
        imageX: slot.x + Math.floor((slot.width - unit.width) / 2),
        imageY: slot.y + Math.floor((slot.height - unit.height) / 2),
      };
    }),
  };
}

async function composeSheet(sheet, outputPath, outputFormat) {
  const args = ["-size", `${sheet.width}x${sheet.height}`, "canvas:none"];

  for (const frame of sheet.frames) {
    args.push(frame.imagePath, "-geometry", `+${frame.imageX}+${frame.imageY}`, "-composite");
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
