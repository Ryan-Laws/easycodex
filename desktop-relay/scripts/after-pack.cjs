const fs = require('node:fs');
const path = require('node:path');

exports.default = async function afterPack(context) {
  if (context.electronPlatformName !== 'win32') {
    return;
  }

  const iconPath = path.join(context.packager.projectDir, 'build', 'icon.ico');
  const exePath = path.join(
    context.appOutDir,
    `${context.packager.appInfo.productFilename}.exe`,
  );

  if (!fs.existsSync(iconPath) || !fs.existsSync(exePath)) {
    throw new Error(`Cannot apply Windows icon. Missing ${iconPath} or ${exePath}.`);
  }

  const { rcedit } = await import('rcedit');
  await rcedit(exePath, { icon: iconPath });
  console.log(`Applied Windows app icon to ${exePath}`);
};
