import { execFileSync } from 'node:child_process';
import { appendFileSync, readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const PATCH = 'version-update:semver-patch';
const REPOSITORY = 'everdein/pay-period-planner';
const ALLOWED_TOOLS = {
  '/': new Set(['cspell', 'prettier']),
  '/frontend': new Set([
    '@axe-core/playwright',
    '@eslint/js',
    '@playwright/test',
    '@testing-library/jest-dom',
    '@testing-library/react',
    '@types/node',
    '@types/react',
    '@types/react-dom',
    '@typescript-eslint/eslint-plugin',
    '@typescript-eslint/parser',
    '@vitest/coverage-v8',
    'eslint',
    'eslint-config-prettier',
    'eslint-plugin-jsx-a11y',
    'eslint-plugin-react',
    'eslint-plugin-react-hooks',
    'eslint-plugin-react-refresh',
    'eslint-plugin-simple-import-sort',
    'globals',
    'typescript',
    'vitest',
  ]),
};

function stablePatch(previous, next) {
  const parse = (value) =>
    typeof value === 'string' && value.match(/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/);
  const from = parse(previous);
  const to = parse(next);
  return Boolean(
    from && to && from[1] === to[1] && from[2] === to[2] && BigInt(to[3]) > BigInt(from[3])
  );
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, canonical(value[key])])
    );
  }
  return value;
}

function same(left, right) {
  return JSON.stringify(canonical(left)) === JSON.stringify(canonical(right));
}

/** Pure metadata/file gate. It enables no merge and never executes PR code. */
export function assessPatchEligibility({ event, pr, metadata, files, manifests }) {
  const reject = (reason) => ({ eligible: false, reason });
  if (
    event?.repository?.full_name !== REPOSITORY ||
    pr?.base?.repo?.full_name !== REPOSITORY ||
    pr?.head?.repo?.full_name !== REPOSITORY ||
    pr?.base?.ref !== 'main' ||
    pr?.user?.login !== 'dependabot[bot]' ||
    pr?.state !== 'open' ||
    pr?.draft !== false
  )
    return reject('PR identity, base, author or state is ineligible.');
  const eventPr = event.pull_request;
  if (
    !eventPr ||
    pr.number !== eventPr.number ||
    eventPr.user?.login !== 'dependabot[bot]' ||
    !/^[a-f0-9]{40}$/.test(pr.head.sha) ||
    eventPr.head?.sha !== pr.head.sha
  ) {
    return reject('The event head is stale or unverified.');
  }
  if (
    metadata?.updateType !== PATCH ||
    metadata?.maintainerChanges !== 'false' ||
    !Array.isArray(metadata.dependencies) ||
    metadata.dependencies.length === 0 ||
    metadata.dependencies.length > 50
  )
    return reject('Patch metadata is missing, mixed, or requires maintainer review.');
  const dependencies = metadata.dependencies;
  const directory = dependencies[0].directory;
  const tools = ALLOWED_TOOLS[directory];
  if (
    !tools ||
    dependencies.some(
      (dependency) =>
        dependency.directory !== directory ||
        dependency.packageEcosystem !== 'npm_and_yarn' ||
        dependency.targetBranch !== 'main' ||
        dependency.dependencyType !== 'direct:development' ||
        dependency.updateType !== PATCH ||
        dependency.maintainerChanges !== false ||
        !tools.has(dependency.dependencyName) ||
        !stablePatch(dependency.prevVersion, dependency.newVersion)
    )
  ) {
    return reject('Only allowlisted stable npm development-tool patch changes qualify.');
  }
  const names = dependencies.map(({ dependencyName }) => dependencyName);
  if (new Set(names).size !== names.length)
    return reject('Duplicate dependency metadata is ambiguous.');
  const prefix = directory === '/' ? '' : 'frontend/';
  const allowedFiles = new Set([`${prefix}package.json`, `${prefix}package-lock.json`]);
  if (
    !Array.isArray(files) ||
    !Number.isInteger(pr.changed_files) ||
    pr.changed_files < 1 ||
    pr.changed_files > 100 ||
    files.length !== pr.changed_files ||
    new Set(files.map(({ filename }) => filename)).size !== files.length ||
    files.some(
      (file) =>
        !allowedFiles.has(file.filename) || file.status !== 'modified' || file.previous_filename
    )
  )
    return reject('Changed-file evidence is incomplete or outside the dependency-only scope.');
  if (!files.some(({ filename }) => filename === `${prefix}package.json`)) {
    return reject('A manifest change is required for direct-development metadata.');
  }
  if (!files.some(({ filename }) => filename === `${prefix}package-lock.json`)) {
    return reject('The matching committed lockfile must change with the manifest.');
  }
  const before = manifests?.before;
  const after = manifests?.after;
  if (!before?.devDependencies || !after?.devDependencies)
    return reject('Manifest evidence is unavailable.');
  const omit = ({ devDependencies, ...rest }) => rest;
  if (!same(omit(before), omit(after)))
    return reject('Manifest scripts, runtime dependencies or other non-tooling fields changed.');
  const allNames = new Set([
    ...Object.keys(before.devDependencies),
    ...Object.keys(after.devDependencies),
  ]);
  const changed = [...allNames].filter(
    (name) => before.devDependencies[name] !== after.devDependencies[name]
  );
  if (
    !same([...changed].sort(), [...names].sort()) ||
    dependencies.some((dependency) => {
      const oldRange = before.devDependencies[dependency.dependencyName];
      const newRange = after.devDependencies[dependency.dependencyName];
      const strip = (value) => (typeof value === 'string' ? value.replace(/^[~^]/, '') : '');
      return (
        strip(oldRange) !== dependency.prevVersion ||
        strip(newRange) !== dependency.newVersion ||
        oldRange.replace(dependency.prevVersion, '') !== newRange.replace(dependency.newVersion, '')
      );
    })
  )
    return reject('Manifest changes do not match the declared dependency patch metadata.');
  return {
    eligible: true,
    reason:
      'Allowlisted development-tool patches; dependency-only file scope and reviewed event head match.',
    head: pr.head.sha,
  };
}

function githubRead(endpoint, raw = false) {
  const args = ['api', '--method', 'GET', endpoint];
  if (raw) args.push('--header', 'Accept: application/vnd.github.raw+json');
  const output = execFileSync('gh', args, {
    encoding: 'utf8',
    timeout: 30000,
    maxBuffer: 8 * 1024 * 1024,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  return JSON.parse(output);
}

export function collectEligibility({ event, metadata, read = githubRead }) {
  if (
    event?.repository?.full_name !== REPOSITORY ||
    !Number.isInteger(event?.pull_request?.number)
  ) {
    return { eligible: false, reason: 'Unrecognized event repository or PR number.' };
  }
  const pr = read(`repos/${REPOSITORY}/pulls/${event.pull_request.number}`);
  if (
    pr?.head?.sha !== event.pull_request.head?.sha ||
    !/^[a-f0-9]{40}$/.test(pr?.base?.sha ?? '')
  ) {
    return {
      eligible: false,
      reason: 'The current PR head/base differs from the event or is unavailable.',
    };
  }
  const files = read(`repos/${REPOSITORY}/pulls/${pr.number}/files?per_page=100`);
  const directory = metadata?.dependencies?.[0]?.directory;
  if (!Object.hasOwn(ALLOWED_TOOLS, directory ?? '')) {
    return { eligible: false, reason: 'The ecosystem or directory requires manual review.' };
  }
  const path = directory === '/' ? 'package.json' : 'frontend/package.json';
  const before = read(`repos/${REPOSITORY}/contents/${path}?ref=${pr.base.sha}`, true);
  const after = read(`repos/${REPOSITORY}/contents/${path}?ref=${pr.head.sha}`, true);
  return assessPatchEligibility({ event, pr, metadata, files, manifests: { before, after } });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  let decision;
  try {
    const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'));
    const metadata = {
      updateType: process.env.UPDATE_TYPE,
      maintainerChanges: process.env.MAINTAINER_CHANGES,
      dependencies: JSON.parse(process.env.UPDATED_DEPENDENCIES_JSON ?? 'null'),
    };
    decision = collectEligibility({ event, metadata });
  } catch {
    decision = {
      eligible: false,
      reason: 'Required provider or metadata evidence was unavailable; manual review required.',
    };
    process.exitCode = 1;
  }
  if (process.env.GITHUB_OUTPUT)
    appendFileSync(process.env.GITHUB_OUTPUT, `eligible=${decision.eligible}\n`);
  if (process.env.GITHUB_STEP_SUMMARY)
    appendFileSync(
      process.env.GITHUB_STEP_SUMMARY,
      `## Dependabot patch eligibility\n\n${decision.reason}\n`
    );
  console.log(JSON.stringify(decision));
}
