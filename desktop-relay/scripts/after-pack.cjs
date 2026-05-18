const fs = require('node:fs');
const path = require('node:path');

const WINDOWS_GPU_RUNTIME_FILES = [
  'd3dcompiler_47.dll',
  'dxcompiler.dll',
  'dxil.dll',
  'vk_swiftshader.dll',
  'vulkan-1.dll',
  'vk_swiftshader_icd.json',
];

function pruneWindowsGpuRuntimeFiles(appOutDir) {
  for (const fileName of WINDOWS_GPU_RUNTIME_FILES) {
    const targetPath = path.join(appOutDir, fileName);
    if (!fs.existsSync(targetPath)) continue;
    fs.unlinkSync(targetPath);
    console.log(`Removed unused Windows GPU runtime file ${targetPath}`);
  }
}

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

  pruneWindowsGpuRuntimeFiles(context.appOutDir);
};
