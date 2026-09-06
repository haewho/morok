#!/usr/bin/env node
// Optional vector rasterization with sharp/libvips/librsvg; no bitmap reference is edited.
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');

const root = __dirname;
async function render(source, target, width, height = width) {
  const targetPath = path.join(root, target);
  await fs.mkdir(path.dirname(targetPath), { recursive: true });
  await sharp(path.join(root, source), { density: 384 })
    .resize(width, height)
    .png()
    .toFile(targetPath);
  const info = await sharp(targetPath).metadata();
  if (info.width !== width || info.height !== height) {
    throw new Error(`Unexpected output size: ${target}`);
  }
}

async function main() {
  for (const [density, size] of Object.entries({ mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 })) {
    await render('morok-dark.svg', `android/mipmap-${density}/morok_launcher.png`, size);
    await render('previews/legacy-round.svg', `android/mipmap-${density}/morok_launcher_round.png`, size);
  }
  await render('morok-dark.svg', 'previews/morok-dark.png', 512);
  await render('morok-light.svg', 'previews/morok-light.png', 512);
  await render('previews/launcher-masks.svg', 'previews/launcher-masks.png', 960, 420);
  await fs.writeFile(path.join(root, 'previews/rasterizer.json'), JSON.stringify({
    renderer: 'sharp / libvips / librsvg',
    versions: sharp.versions,
    outputs: 13,
    note: 'Generated from SVG; Android rendering and device installation are separate checks.'
  }, null, 2) + '\n');
  console.log('Generated and checked dimensions of 10 launcher PNGs and 3 previews.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
