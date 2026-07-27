import { globSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

// 自动发现全部测试文件，避免手工维护 package.json 清单导致 CI 漏跑。
const testsDirectory = fileURLToPath(new URL('../tests/', import.meta.url))
const testFiles = globSync('**/*.test.ts', { cwd: testsDirectory }).sort()

for (const [index, testFile] of testFiles.entries()) {
  console.log(`[web:test] ${index + 1}/${testFiles.length} ${testFile}`)
  const result = spawnSync(process.execPath, ['--experimental-strip-types', join(testsDirectory, testFile)], {
    env: process.env,
    stdio: 'inherit',
  })
  if (result.status !== 0) {
    process.exit(result.status || 1)
  }
}

console.log(`[web:test] passed ${testFiles.length}/${testFiles.length} files`)
