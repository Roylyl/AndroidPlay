#!/usr/bin/env node
// Deterministic SVG -> Android VectorDrawable/adaptive icons + required PNGs.
// Requires Node.js and sharp only when regenerating assets, not when building the APK.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const svg = fs.readFileSync(path.join(root, 'asset/androidplay-icon.svg'), 'utf8');
const mark = svg.match(/<g id="mark">([\s\S]*?)<\/g>/)[1];
const background = svg.match(/<path id="background"[^>]*\/>/)[0];
function vectorPaths(fragment, monochrome = false) {
  return [...fragment.matchAll(/<path\s+([^>]+)\/>/g)].map((match) => {
    const attrs = Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map((m) => [m[1], m[2]]));
    const output = {pathData: attrs.d, fillColor: attrs.fill === 'none' ? '#00000000' : monochrome ? '#FFFFFFFF' : attrs.fill};
    if (attrs.stroke) Object.assign(output, {strokeColor: monochrome ? '#FFFFFFFF' : attrs.stroke, strokeWidth: attrs['stroke-width'], strokeLineCap: attrs['stroke-linecap'], strokeLineJoin: attrs['stroke-linejoin']});
    return '    <path ' + Object.entries(output).map(([key, value]) => `android:${key}="${value}"`).join(' ') + '/>';
  }).join('\n');
}
function vector(fragment, monochrome = false, notification = false) {
  let paths = vectorPaths(fragment, monochrome);
  if (notification) paths = `  <group android:translateX="-18" android:translateY="-18">\n${paths}\n  </group>`;
  return `<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="${notification ? 24 : 108}dp" android:height="${notification ? 24 : 108}dp" android:viewportWidth="${notification ? 72 : 108}" android:viewportHeight="${notification ? 72 : 108}">\n${paths}\n</vector>\n`;
}
function write(relative, content) {
  const file = path.join(root, relative); fs.mkdirSync(path.dirname(file), {recursive:true}); fs.writeFileSync(file, content);
}
function adaptive(monochrome) {
  return `<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/androidplay_icon_background"/>\n    <foreground android:drawable="@drawable/ic_androidplay_foreground"/>\n${monochrome ? '    <monochrome android:drawable="@drawable/ic_androidplay_monochrome"/>\n' : ''}</adaptive-icon>\n`;
}
async function main() {
  write('common/src/main/res/drawable/ic_androidplay.xml', vector(background + mark));
  write('common/src/main/res/drawable/ic_androidplay_notification.xml', vector(mark, true, true));
  write('shared/src/main/res/drawable/ic_androidplay_foreground.xml', vector(mark));
  write('shared/src/main/res/drawable/ic_androidplay_monochrome.xml', vector(mark, true));
  write('shared/src/main/res/values/androidplay_icon.xml', '<resources>\n    <color name="androidplay_icon_background">#0C111B</color>\n</resources>\n');
  for (const name of ['ic_launcher','ic_launcher_round']) {
    write(`shared/src/main/res/mipmap-anydpi/${name}.xml`, vector(background + mark));
    write(`shared/src/main/res/mipmap-anydpi-v26/${name}.xml`, adaptive(false));
    write(`shared/src/main/res/mipmap-anydpi-v33/${name}.xml`, adaptive(true));
  }
  const pngs = [
    ['asset/androidplay-icon.png',512],
    ['common/src/main/res/raw/androidplay_carplay_icon.png',192],
    ['shared/src/main/ic_launcher-playstore.png',512],
    ['site/assets/icon.png',192],
  ];
  for (const [relative, size] of pngs) {
    const file = path.join(root, relative); fs.mkdirSync(path.dirname(file),{recursive:true});
    await sharp(Buffer.from(svg)).resize(size,size).png().toFile(file);
  }
  // Visual QA sheet, explicitly icon previews rather than application screenshots.
  const full = background + `<g>${mark}</g>`;
  const mono = mark.replaceAll('#A6C8FF','#FFFFFF').replaceAll('#F4F8FF','#FFFFFF');
  const preview = `<svg xmlns="http://www.w3.org/2000/svg" width="760" height="300" viewBox="0 0 760 300"><rect width="760" height="300" fill="#EAF0F7"/><defs><clipPath id="circle"><circle cx="54" cy="54" r="54"/></clipPath></defs><svg x="28" y="42" width="216" height="216" viewBox="0 0 108 108">${full}</svg><svg x="288" y="42" width="108" height="108" viewBox="0 0 108 108"><g clip-path="url(#circle)"><rect width="108" height="108" fill="#0C111B"/>${mark}</g></svg><svg x="320" y="190" width="48" height="48" viewBox="0 0 108 108">${full}</svg><rect x="448" y="42" width="264" height="216" rx="24" fill="#0C111B"/><svg x="496" y="80" width="96" height="96" viewBox="18 18 72 72">${mono}</svg><svg x="648" y="116" width="24" height="24" viewBox="18 18 72 72">${mono}</svg></svg>`;
  fs.mkdirSync(path.join(root,'build'),{recursive:true});
  await sharp(Buffer.from(preview)).png().toFile(path.join(root,'build/icon-preview.png'));
  console.log('Generated AndroidPlay vectors, adaptive/monochrome icons, PNGs and icon preview.');
}
main().catch((error) => {console.error(error.message);process.exit(1);});
