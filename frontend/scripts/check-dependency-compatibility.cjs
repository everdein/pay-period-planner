const fs = require('node:fs');
const { createRequire } = require('node:module');
const process = require('node:process');

const legacyMinimatchOwners = [
  '@eslint/config-array',
  '@eslint/eslintrc',
  'eslint',
  'eslint-plugin-jsx-a11y',
  'eslint-plugin-react',
];
const expectedBraceExpansionVersion = '1.1.13';
let legacyMinimatchCount = 0;

for (const owner of legacyMinimatchOwners) {
  const ownerRequire = createRequire(require.resolve(owner));
  const minimatchPackagePath = ownerRequire.resolve('minimatch/package.json');
  const minimatchPackage = JSON.parse(fs.readFileSync(minimatchPackagePath, 'utf8'));

  if (!minimatchPackage.version.startsWith('3.')) {
    continue;
  }

  legacyMinimatchCount += 1;
  const minimatchRequire = createRequire(minimatchPackagePath);
  const braceExpansionPackagePath = minimatchRequire.resolve('brace-expansion/package.json');
  const braceExpansionPackage = JSON.parse(fs.readFileSync(braceExpansionPackagePath, 'utf8'));

  if (braceExpansionPackage.version !== expectedBraceExpansionVersion) {
    throw new Error(
      `${owner} resolves minimatch ${minimatchPackage.version} with ` +
        `brace-expansion ${braceExpansionPackage.version}; expected ` +
        `${expectedBraceExpansionVersion}`
    );
  }

  const minimatch = minimatchRequire('minimatch');
  if (!minimatch('report-daily.md', 'report-{daily,weekly}.md')) {
    throw new Error(`${owner} failed the legacy minimatch brace-expansion check`);
  }
}

if (legacyMinimatchCount === 0) {
  throw new Error(
    'No configured dependency uses minimatch 3; remove the compatibility override and this check'
  );
}

process.stdout.write(
  `Verified ${legacyMinimatchCount} minimatch 3 dependency paths with ` +
    `brace-expansion ${expectedBraceExpansionVersion}.\n`
);
