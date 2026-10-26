import assert from 'node:assert/strict';
import test from 'node:test';
import { assessPatchEligibility, collectEligibility } from './dependabot-eligibility.mjs';

const SHA = 'a'.repeat(40);
const BASE = 'b'.repeat(40);
function fixture() {
  const repository = { full_name: 'everdein/pay-period-planner' };
  const pr = {
    number: 7,
    user: { login: 'dependabot[bot]' },
    draft: false,
    state: 'open',
    changed_files: 2,
    head: { sha: SHA, repo: repository },
    base: { sha: BASE, ref: 'main', repo: repository },
  };
  return {
    event: { repository, pull_request: structuredClone(pr) },
    pr,
    metadata: {
      updateType: 'version-update:semver-patch',
      maintainerChanges: 'false',
      dependencies: [
        {
          dependencyName: 'prettier',
          dependencyType: 'direct:development',
          packageEcosystem: 'npm_and_yarn',
          directory: '/',
          targetBranch: 'main',
          updateType: 'version-update:semver-patch',
          maintainerChanges: false,
          prevVersion: '3.9.10',
          newVersion: '3.9.11',
        },
      ],
    },
    files: [
      { filename: 'package.json', status: 'modified' },
      { filename: 'package-lock.json', status: 'modified' },
    ],
    manifests: {
      before: { scripts: { test: 'node --test' }, devDependencies: { prettier: '^3.9.10' } },
      after: { scripts: { test: 'node --test' }, devDependencies: { prettier: '^3.9.11' } },
    },
  };
}
test('an exact-head allowlisted development patch is eligible; grouped patches remain eligible', () => {
  const value = fixture();
  assert.equal(assessPatchEligibility(value).eligible, true);
  value.metadata.dependencies.push({
    ...value.metadata.dependencies[0],
    dependencyName: 'cspell',
    prevVersion: '10.4.0',
    newVersion: '10.4.1',
  });
  value.manifests.before.devDependencies.cspell = '^10.4.0';
  value.manifests.after.devDependencies.cspell = '^10.4.1';
  assert.equal(assessPatchEligibility(value).eligible, true);
});
const cases = {
  'stale head': (v) => {
    v.pr.head.sha = 'c'.repeat(40);
  },
  nonbot: (v) => {
    v.pr.user.login = 'someone';
  },
  draft: (v) => {
    v.pr.draft = true;
  },
  closed: (v) => {
    v.pr.state = 'closed';
  },
  fork: (v) => {
    v.pr.head.repo = { full_name: 'other/repo' };
  },
  'mixed aggregate minor': (v) => {
    v.metadata.updateType = 'version-update:semver-minor';
  },
  'per-dependency minor': (v) => {
    v.metadata.dependencies[0].updateType = 'version-update:semver-minor';
  },
  'missing metadata': (v) => {
    v.metadata.dependencies = [];
  },
  'maintainer change': (v) => {
    v.metadata.maintainerChanges = 'true';
  },
  'per-dependency maintainer change': (v) => {
    v.metadata.dependencies[0].maintainerChanges = true;
  },
  'runtime package': (v) => {
    v.metadata.dependencies[0].dependencyType = 'direct:production';
  },
  'unknown package': (v) => {
    v.metadata.dependencies[0].dependencyName = 'unknown-tool';
  },
  maven: (v) => {
    v.metadata.dependencies[0].packageEcosystem = 'maven';
  },
  'minor version despite patch label': (v) => {
    v.metadata.dependencies[0].newVersion = '3.10.0';
  },
  prerelease: (v) => {
    v.metadata.dependencies[0].newVersion = '3.9.11-beta.1';
  },
  downgrade: (v) => {
    v.metadata.dependencies[0].newVersion = '3.9.9';
  },
  'workflow permissions file': (v) => {
    v.files[1].filename = '.github/workflows/ci.yml';
  },
  'auth implementation': (v) => {
    v.files[1].filename = 'backend/src/main/java/Auth.java';
  },
  migration: (v) => {
    v.files[1].filename = 'backend/src/main/resources/db/migration/V99.sql';
  },
  'renamed file': (v) => {
    v.files[1].status = 'renamed';
    v.files[1].previous_filename = 'secret';
  },
  'partial page': (v) => {
    v.pr.changed_files = 101;
  },
  'missing file': (v) => {
    v.pr.changed_files = 3;
  },
  'missing lockfile change': (v) => {
    v.pr.changed_files = 1;
    v.files = v.files.slice(0, 1);
  },
  'script modification': (v) => {
    v.manifests.after.scripts.test = 'run unexpected';
  },
  'engine modification': (v) => {
    v.manifests.after.engines = { node: '*' };
  },
  'unlisted manifest change': (v) => {
    v.manifests.after.devDependencies.prettier = '^3.9.12';
  },
  'range policy change': (v) => {
    v.manifests.after.devDependencies.prettier = '~3.9.11';
  },
};
for (const [name, mutate] of Object.entries(cases)) {
  test(`rejects ${name}`, () => {
    const value = fixture();
    mutate(value);
    assert.equal(assessPatchEligibility(value).eligible, false);
  });
}
test('collector uses bounded GET endpoints and rejects unavailable evidence', () => {
  const value = fixture();
  const calls = [];
  const read = (endpoint, raw) => {
    calls.push([endpoint, raw]);
    if (endpoint.endsWith('/pulls/7')) return value.pr;
    if (endpoint.endsWith('/files?per_page=100')) return value.files;
    if (endpoint.endsWith(`ref=${BASE}`)) return value.manifests.before;
    if (endpoint.endsWith(`ref=${SHA}`)) return value.manifests.after;
    throw new Error('Unexpected read');
  };
  assert.equal(collectEligibility({ ...value, read }).eligible, true);
  assert.equal(calls.length, 4);
  assert.throws(
    () =>
      collectEligibility({
        ...value,
        read: () => {
          throw new Error('denied');
        },
      }),
    /denied/
  );
});
