const fs = require('fs');
const path = require('path');
const icongen = require('icon-gen');

const projectRoot = path.resolve(__dirname, '..');
const assetSource = path.join(projectRoot, 'assets', 'app-icon.svg');
const iconOutputDir = path.join(projectRoot, 'build', 'icons');

async function main() {
  fs.mkdirSync(iconOutputDir, { recursive: true });
  await icongen(assetSource, iconOutputDir, {
    report: false,
    ico: {
      name: 'icon',
      sizes: [16, 24, 32, 48, 64, 128, 256]
    },
    icns: {
      name: 'icon',
      sizes: [16, 32, 64, 128, 256, 512, 1024]
    },
    favicon: {
      name: 'icon-',
      pngSizes: [32, 64, 128, 256, 512],
      icoSizes: [16, 24, 32, 48, 64]
    }
  });
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
