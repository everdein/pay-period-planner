const fs = require('node:fs');
const path = require('node:path');

const packageJsonPath = require.resolve('brace-expansion/package.json');
const commonJsEntry = path.join(path.dirname(packageJsonPath), 'dist', 'commonjs', 'index.js');
const compatibilityMarker = '// end-to-end-app: minimatch 3 CommonJS compatibility';
const source = fs.readFileSync(commonJsEntry, 'utf8');

if (!source.includes(compatibilityMarker)) {
  if (!source.includes('exports.expand = expand;')) {
    throw new Error(`Unexpected brace-expansion CommonJS entry: ${commonJsEntry}`);
  }

  const compatibilityPatch = `
${compatibilityMarker}
const namedExports = module.exports;
module.exports = expand;
module.exports.expand = expand;
module.exports.EXPANSION_MAX = namedExports.EXPANSION_MAX;
`;

  fs.writeFileSync(commonJsEntry, `${source.trimEnd()}\n${compatibilityPatch}`, 'utf8');
}
