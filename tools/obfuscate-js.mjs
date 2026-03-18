import fs from "node:fs/promises";
import path from "node:path";
import { build, transform } from "esbuild";
import JavaScriptObfuscator from "javascript-obfuscator";

const projectRoot = process.cwd();
const templatesDir = path.join(projectRoot, "src", "main", "resources", "templates");
const jsSourceRoot = path.join(projectRoot, "src", "main", "resources", "static", "assets", "js");
const cssSourceRoot = path.join(projectRoot, "src", "main", "resources", "static", "assets", "css");
const jsOutputRoot = path.join(projectRoot, "target", "classes", "static", "assets", "js");
const cssOutputRoot = path.join(projectRoot, "target", "classes", "static", "assets", "css");
const scriptPathRegex = /assets\/js\/([A-Za-z0-9_./-]+\.js)/g;
const cssPathRegex = /assets\/css\/([A-Za-z0-9_./-]+\.css)/g;
const bundleExcludeEntries = new Set([
  "vendor/superset-embedded-sdk.js"
]);

const obfuscationLevel = (process.env.JS_OBFUSCATION_LEVEL || "high").toLowerCase();
const obfuscationProfiles = {
  normal: {
    compact: true,
    identifierNamesGenerator: "hexadecimal",
    renameGlobals: false,
    stringArray: true,
    stringArrayEncoding: ["base64"],
    stringArrayThreshold: 0.75,
    target: "browser",
    unicodeEscapeSequence: false
  },
  high: {
    compact: true,
    controlFlowFlattening: true,
    controlFlowFlatteningThreshold: 0.75,
    deadCodeInjection: true,
    deadCodeInjectionThreshold: 0.1,
    disableConsoleOutput: false,
    identifierNamesGenerator: "hexadecimal",
    numbersToExpressions: true,
    renameGlobals: false,
    selfDefending: true,
    splitStrings: true,
    splitStringsChunkLength: 8,
    stringArray: true,
    stringArrayEncoding: ["base64"],
    stringArrayThreshold: 1,
    target: "browser",
    unicodeEscapeSequence: false
  }
};
const obfuscationOptions = obfuscationProfiles[obfuscationLevel] || obfuscationProfiles.high;

async function walkFiles(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const files = await Promise.all(
    entries.map(async (entry) => {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        return walkFiles(fullPath);
      }
      return [fullPath];
    })
  );
  return files.flat();
}

async function collectEntriesFromTemplates(pathRegex) {
  const allFiles = await walkFiles(templatesDir);
  const htmlFiles = allFiles.filter((file) => file.endsWith(".html"));
  const assetEntries = new Set();

  for (const file of htmlFiles) {
    const content = await fs.readFile(file, "utf8");
    for (const match of content.matchAll(pathRegex)) {
      if (match[1]) {
        assetEntries.add(match[1]);
      }
    }
  }
  return Array.from(assetEntries).sort();
}

async function fileExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function bundleAndObfuscate(entryRelPath) {
  const sourcePath = path.join(jsSourceRoot, entryRelPath);
  const exists = await fileExists(sourcePath);
  if (!exists) {
    throw new Error(`Missing JS entry: ${entryRelPath}`);
  }

  const result = await build({
    entryPoints: [sourcePath],
    bundle: true,
    charset: "utf8",
    format: "iife",
    legalComments: "none",
    minify: true,
    platform: "browser",
    sourcemap: false,
    target: ["es2019"],
    write: false
  });

  const bundledCode = result.outputFiles[0]?.text ?? "";
  const obfuscatedCode = JavaScriptObfuscator.obfuscate(bundledCode, obfuscationOptions).getObfuscatedCode();
  const outputPath = path.join(jsOutputRoot, entryRelPath);
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, obfuscatedCode, "utf8");
}

async function copyEntryAsIs(entryRelPath) {
  const sourcePath = path.join(jsSourceRoot, entryRelPath);
  const exists = await fileExists(sourcePath);
  if (!exists) {
    throw new Error(`Missing JS entry: ${entryRelPath}`);
  }
  const outputPath = path.join(jsOutputRoot, entryRelPath);
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.copyFile(sourcePath, outputPath);
}

async function copyUnbundledSupportModules(entryRelPaths) {
  const allSourceFiles = await walkFiles(jsSourceRoot);
  const sourceModules = allSourceFiles
    .filter((file) => file.endsWith(".js"))
    .map((file) => path.relative(jsSourceRoot, file).replaceAll("\\", "/"));

  const entrySet = new Set(entryRelPaths.map((entry) => entry.replaceAll("\\", "/")));
  const supportModules = sourceModules.filter((modulePath) => !entrySet.has(modulePath));

  await Promise.all(
    supportModules.map(async (modulePath) => {
      const sourcePath = path.join(jsSourceRoot, modulePath);
      const outputPath = path.join(jsOutputRoot, modulePath);
      await fs.mkdir(path.dirname(outputPath), { recursive: true });
      await fs.copyFile(sourcePath, outputPath);
    })
  );

  return supportModules.length;
}

async function minifyCss(entryRelPath) {
  const sourcePath = path.join(cssSourceRoot, entryRelPath);
  const exists = await fileExists(sourcePath);
  if (!exists) {
    throw new Error(`Missing CSS entry: ${entryRelPath}`);
  }

  const cssCode = await fs.readFile(sourcePath, "utf8");
  const minified = await transform(cssCode, {
    charset: "utf8",
    legalComments: "none",
    loader: "css",
    minify: true
  });

  const outputPath = path.join(cssOutputRoot, entryRelPath);
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, minified.code, "utf8");
}

async function main() {
  const jsEntries = await collectEntriesFromTemplates(scriptPathRegex);
  const cssEntries = await collectEntriesFromTemplates(cssPathRegex);
  if (jsEntries.length === 0 && cssEntries.length === 0) {
    console.log("No JS or CSS entries found in templates. Skipping asset processing.");
    return;
  }

  if (jsEntries.length > 0) {
    const normalizedEntries = jsEntries.map((entry) => entry.replaceAll("\\", "/"));
    const passthroughEntries = normalizedEntries.filter((entry) => bundleExcludeEntries.has(entry));
    const bundledEntries = normalizedEntries.filter((entry) => !bundleExcludeEntries.has(entry));

    console.log(`Using JS obfuscation level: ${obfuscationLevel}`);
    await fs.rm(jsOutputRoot, { force: true, recursive: true });
    await Promise.all(bundledEntries.map((entry) => bundleAndObfuscate(entry)));
    await Promise.all(passthroughEntries.map((entry) => copyEntryAsIs(entry)));
    const copiedSupportModules = await copyUnbundledSupportModules(normalizedEntries);
    console.log(`Obfuscated ${bundledEntries.length} browser JS bundle(s) into ${path.relative(projectRoot, jsOutputRoot)}.`);
    if (passthroughEntries.length > 0) {
      console.log(`Copied ${passthroughEntries.length} JS entry file(s) without bundling: ${passthroughEntries.join(", ")}.`);
    }
    console.log(`Copied ${copiedSupportModules} support JS module(s) into ${path.relative(projectRoot, jsOutputRoot)}.`);
  } else {
    console.log("No JS entries found in templates. Skipping JS obfuscation.");
  }

  if (cssEntries.length > 0) {
    await Promise.all(cssEntries.map((entry) => minifyCss(entry)));
    console.log(`Minified ${cssEntries.length} CSS file(s) into ${path.relative(projectRoot, cssOutputRoot)}.`);
  } else {
    console.log("No CSS entries found in templates. Skipping CSS minification.");
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
