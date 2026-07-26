import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const router = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')
const sidebar = readFileSync(new URL('../src/components/layout/AppSidebar.vue', import.meta.url), 'utf8')
const switcherFile = new URL('../src/components/layout/ProfileSwitcher.vue', import.meta.url)
const agents = readFileSync(new URL('../src/views/solonclaw/AgentsView.vue', import.meta.url), 'utf8')
const view = readFileSync(new URL('../src/views/solonclaw/ProfilesView.vue', import.meta.url), 'utf8')
const builder = readFileSync(new URL('../src/views/solonclaw/ProfileBuilderView.vue', import.meta.url), 'utf8')
const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const client = readFileSync(new URL('../src/api/client.ts', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/api/solonclaw/profiles.ts', import.meta.url), 'utf8')

assert.ok(router.includes("path: '/profiles'"), 'router should expose the Profiles page')
assert.ok(router.includes("path: '/profiles/new'"), 'router should expose the dedicated stepped Profile builder')
assert.ok(router.includes("name: 'solonclaw.profiles.new'"), 'Profile builder should have a stable named route')
assert.equal(existsSync(switcherFile), false, 'the global Profile switcher should be removed')
assert.ok(!sidebar.includes('ProfileSwitcher'), 'the sidebar should not expose manual Profile switching')
assert.ok(
  app.includes(':key="profilesStore.managedProfileName"'),
  'Profile-scoped deep links should remount the routed page tree and clear stale local state',
)
assert.ok(
  app.includes("profilesStore.setManagementProfile(typeof value === 'string' ? value : '')"),
  'the route query should be the only source of Profile management scope',
)
assert.ok(
  !app.includes('router.replace({ query })'),
  'leaving a Profile deep link must not propagate its Profile into unrelated routes',
)
assert.ok(client.includes('profiledApiPath'), 'the shared API client should inject a Profile selected by scoped links')

for (const action of [
  'createProfile',
  'renameProfile',
  'deleteProfile',
  'importProfile',
  'exportProfile',
  'updateProfileDescription',
  'describeProfileAutomatically',
  'fetchProfileSoul',
  'updateProfileSoul',
  'updateProfileModel',
  'createProfileAlias',
  'removeProfileAlias',
  'installProfileDistribution',
  'updateProfileDistribution',
  'searchProfileHubSkills',
]) {
  assert.ok(api.includes(`function ${action}`), `Profiles API should expose ${action}`)
}
for (const field of ['clone_from_default', 'provider', 'model', 'keep_skills', 'hub_skills']) {
  assert.ok(api.includes(`${field}?:`), `full Profile create contract should include ${field}`)
}
assert.ok(api.includes('`/api/skills/hub/search?${params.toString()}`'), 'Profile builder should use the Skills Hub search contract')
assert.ok(api.includes('force_config: forceConfig'), 'distribution updates should expose force_config semantics')

for (const routeName of [
  'solonclaw.skills',
  'solonclaw.persona.journal',
  'solonclaw.runs',
  'solonclaw.channels',
]) {
  assert.ok(agents.includes(`name: '${routeName}'`), `Agent cards should expose ${routeName} next to task actions`)
}
assert.ok(agents.includes('@click="openTasks(profile)"'), 'Agent cards should expose the task viewer')
assert.ok(agents.includes('v-for="surface in surfaceRoutes"'), 'Profile information links should share the task-action footer')
assert.ok(!agents.includes('configure('), 'Agent cards should not expose a configure action')
assert.ok(!agents.includes('requestGateway'), 'Agent cards should not expose gateway lifecycle actions')
assert.ok(!agents.includes('setActiveProfile'), 'Agent cards should not expose a sticky active Profile action')
assert.ok(agents.includes("task.status === 'RUNNING'"), 'RUNNING ProfileTasks should produce working activity')
assert.ok(
  agents.includes("task.status === 'PENDING' || task.status === 'READY'"),
  'PENDING and READY ProfileTasks should produce waiting activity',
)
assert.ok(agents.includes("task.status === 'BLOCKED'"), 'BLOCKED ProfileTasks should produce blocked activity')
assert.ok(agents.includes("return 'idle'"), 'Profiles without active task states should be idle')
assert.ok(agents.includes('setInterval(() =>'), 'Profile activity should refresh while the page remains open')
assert.ok(agents.includes('void loadAgentTasks()'), 'the activity refresh timer should reload ProfileTasks')
assert.ok(agents.includes('clearInterval(activityRefreshTimer.value)'), 'the activity refresh timer should be released on unmount')

for (const action of [
  "openEditor(profile, 'model')",
  "openEditor(profile, 'description')",
  "openEditor(profile, 'soul')",
  "openEditor(profile, 'alias')",
  'openUpdateDistribution(profile)',
]) {
  assert.ok(view.includes(action), `Profiles list should expose ${action}`)
}
assert.ok(!view.includes('requestGateway'), 'Profiles management should not expose start, stop, or restart controls')
assert.ok(!view.includes('activeProfileName'), 'Profiles management should not expose an active Profile summary')
assert.ok(!view.includes('currentProfileName'), 'Profiles management should not expose a current-process Profile summary')
assert.ok(!view.includes('profile.active'), 'Profile cards should not expose an active badge')
assert.ok(!view.includes('profile.current'), 'Profile cards should not expose a current-process badge')
for (const lifecycleApi of [
  'setActiveProfile',
  'fetchProfileGateway',
  'startProfileGateway',
  'stopProfileGateway',
  'restartProfileGateway',
]) {
  assert.ok(!api.includes(`function ${lifecycleApi}`), `Profiles API should not expose ${lifecycleApi}`)
}
assert.ok(view.includes("name: 'solonclaw.profiles.new'"), 'Build should navigate to the dedicated builder')
assert.ok(view.includes('clone_from: cloneFrom.value || null'), 'quick create should preserve explicit clone-source semantics')
assert.ok(view.includes('clone_all: !!cloneFrom.value && cloneAll.value'), 'clone_all should only apply while cloning')
assert.ok(view.includes('no_skills: !cloneFrom.value && noSkills.value'), 'no_skills should only apply to a fresh Profile')
assert.ok(view.includes('watch(cloneFrom'), 'switching to a fresh Profile should clear stale clone_all state')
assert.ok(view.includes('if (!name || name === renameFrom.value)'), 'an unchanged rename should close without an invalid-name error')
assert.ok(view.includes('profile.credentials_exists'), 'Profile cards should surface the configured env badge')
assert.ok(view.includes('function isCurrentEditorRequest'), 'Profile editors should reject stale async responses')
assert.ok(
  (view.match(/const requestId = editorRequestId/g) || []).length >= 4,
  'save, describe, alias removal, and editor loading should pin the Profile editor request',
)
assert.ok(
  view.includes("isCurrentEditorRequest(requestId, profileName, 'description')"),
  'auto-description should not overwrite a different Profile editor after an async response',
)

for (const step of ['identity', 'model', 'skills', 'review']) {
  assert.ok(builder.includes(`'${step}'`), `Profile builder should include the ${step} step`)
}
for (const field of ['provider:', 'model:', 'keep_skills:', 'hub_skills:']) {
  assert.ok(builder.includes(field), `Profile builder should submit ${field}`)
}
assert.ok(builder.includes('clone_from: null'), 'full builder should create a fresh composable Profile')
assert.ok(builder.includes("searchProfileHubSkills(query, 'all', 20)"), 'Hub search should preserve source=all and limit=20')
assert.ok(builder.includes('profilesStore.createProfile({'), 'the final Review action should use one Profile create request')
for (const earlyWrite of ['updateProfileModel(', 'toggleSkill(']) {
  assert.equal(builder.includes(earlyWrite), false, `builder should not write early through ${earlyWrite}`)
}
assert.ok(api.includes('providerLabel: provider.label'), 'Profile model choices should retain the Provider display label')
assert.ok(builder.includes('modelProviderOptions'), 'the Profile builder should expose a Provider select')
assert.ok(builder.includes('handleModelProviderChange'), 'the Profile builder should reset models when Provider changes')
assert.ok(builder.includes('v-model:value="modelName"'), 'the Profile builder should expose a Provider-scoped model select')
assert.ok(!builder.includes('class="choice-list model-list"'), 'the Profile builder should not render models as an ungrouped button list')
assert.ok(view.includes('v-model:value="createModelProvider"'), 'quick Profile creation should select a Provider first')
assert.ok(view.includes('v-model:value="createModelName"'), 'quick Profile creation should select a Provider-scoped model')
assert.ok(view.includes('v-model:value="editorModelProvider"'), 'Profile editing should select a Provider first')
assert.ok(view.includes('v-model:value="editorModelName"'), 'Profile editing should select a Provider-scoped model')
assert.ok(!view.includes("t('models.unregisteredModel'"), 'Profile editing should only expose registered models')
