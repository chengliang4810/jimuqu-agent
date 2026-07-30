import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const diagnosticsApi = readFileSync(new URL('../src/api/solonclaw/diagnostics.ts', import.meta.url), 'utf8')
const view = readFileSync(new URL('../src/views/solonclaw/DiagnosticsView.vue', import.meta.url), 'utf8')
const zh = readFileSync(new URL('../src/i18n/locales/zh.ts', import.meta.url), 'utf8')
const en = readFileSync(new URL('../src/i18n/locales/en.ts', import.meta.url), 'utf8')

assert.ok(diagnosticsApi.includes('fetchDiagnosticsDoctor'), 'diagnostics API should expose doctor fetch')
assert.ok(diagnosticsApi.includes("'/api/diagnostics/doctor'"), 'doctor fetch should call backend doctor endpoint')
assert.ok(diagnosticsApi.includes('outbound_degraded?: boolean'), 'doctor platform state should expose outbound degradation')
assert.ok(diagnosticsApi.includes('outbound_error_code?: string'), 'doctor platform state should expose outbound error codes')
assert.ok(diagnosticsApi.includes('outbound_error_message?: string'), 'doctor platform state should expose outbound error details')
assert.ok(view.includes('doctor.value = await fetchDiagnosticsDoctor()'), 'diagnostics view should load doctor data')
assert.ok(view.includes("t('diagnostics.doctor')"), 'diagnostics view should render doctor panel')
assert.ok(view.includes("t('diagnostics.doctorPlatformOutboundDegraded')"), 'doctor status should distinguish outbound degradation')
assert.ok(view.includes("t('diagnostics.doctorPlatformOutboundError')"), 'doctor details should distinguish outbound errors')
assert.ok(view.includes("!platform.outbound_degraded ? 'success' : 'warning'"), 'doctor should render outbound degradation as warning')
assert.ok(zh.includes("doctor: 'Doctor 诊断'"), 'Chinese locale should include doctor label')
assert.ok(zh.includes("doctorPlatformOutboundDegraded: '出站异常'"), 'Chinese locale should label outbound degradation')
assert.ok(en.includes("doctor: 'Doctor diagnostics'"), 'English locale should include doctor label')
assert.ok(en.includes("doctorPlatformOutboundDegraded: 'Outbound degraded'"), 'English locale should label outbound degradation')
