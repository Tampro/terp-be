// Lists error codes (enum constants of every `implements ErrorCode` enum) that have no entry in the
// message files of the module that declares them, and codes declared by more than one enum.
//
// Layout (microservices): each module has src/main/resources/language/
//   erp-common  -> common.properties, common_vi.properties, common_ko.properties
//   services/<service> -> messages.properties, messages_vi.properties, messages_ko.properties
// A service's MessageSource reads both (basename language/common,language/messages), so a code must be
// in the files of the module that owns the enum. All three locales are checked (en is the base file).
//
//   node scripts/check-error-messages.mjs            full report
//   node scripts/check-error-messages.mjs --changed  only enum files changed vs origin/dev (or HEAD,
//                                                            or every file when the repo has no commits)
//
// Exit code: 0 = nothing missing in the checked set, 1 = something missing or duplicated.
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..')
const LOCALES = ['', '_vi', '_ko']
const onlyChanged = process.argv.includes('--changed')

const walk = (dir, out = []) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'target' || entry.name === 'node_modules' || entry.name.startsWith('.')) continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, out)
    else if (entry.name.endsWith('.java')) out.push(full)
  }
  return out
}
const rel = (file) => path.relative(ROOT, file).split(path.sep).join('/')

let changed = null
if (onlyChanged) {
  const git = (args) => {
    try {
      return execFileSync('git', args, { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] })
    } catch {
      return null
    }
  }
  const base = git(['diff', '--name-only', 'origin/dev']) ?? git(['diff', '--name-only', 'HEAD'])
  changed = base === null ? null : new Set(
    [base, git(['ls-files', '--others', '--exclude-standard']) ?? ''].join('\n').split('\n').filter(Boolean))
  if (changed === null) console.log('(no commit to compare with — checking every enum)')
}

// Modules = the <module> entries of the root pom (erp-common, services/<name>) that have Java sources.
const modules = [...fs.readFileSync(path.join(ROOT, 'pom.xml'), 'utf8').matchAll(/<module>([^<]+)<\/module>/g)]
  .map((m) => m[1].trim())
  .filter((m) => fs.existsSync(path.join(ROOT, m, 'src/main/java')))

const keysOf = (file) => {
  const keys = new Set()
  if (!fs.existsSync(file)) return null
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Za-z0-9_.-]+)\s*=/)
    if (match) keys.add(match[1])
  }
  return keys
}

const owners = new Map() // code -> [enum file]
let failed = false
let total = 0

for (const module of modules) {
  const codes = new Set()
  for (const file of walk(path.join(ROOT, module, 'src/main/java'))) {
    const text = fs.readFileSync(file, 'utf8')
    if (!/enum\s+\w+\s+implements\s+[\w\s,.]*\bErrorCode\b/.test(text)) continue
    const body = text.slice(text.indexOf('{', text.search(/enum\s+\w+/)) + 1)
    // Constants end at the first ';' outside a string literal.
    const constants = body.replace(/"(?:[^"\\]|\\.)*"/gs, '""').split(';')[0]
    for (const match of constants.matchAll(/(?:^|[,\s])([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)\s*(?=\(|,|$)/gm)) {
      owners.set(match[1], [...(owners.get(match[1]) ?? []), rel(file)])
      if (!changed || changed.has(rel(file))) codes.add(match[1])
    }
  }
  if (!codes.size) continue
  total += codes.size
  const base = path.basename(module) === 'erp-common' ? 'common' : 'messages'
  for (const locale of LOCALES) {
    const name = `${module}/src/main/resources/language/${base}${locale}.properties`
    const keys = keysOf(path.join(ROOT, name))
    const missing = keys === null ? [...codes] : [...codes].filter((c) => !keys.has(c))
    if (keys === null) console.log(`\n${name}: FILE MISSING`)
    if (missing.length) {
      failed = true
      console.log(`\n${name}: ${missing.length} missing\n  ${missing.sort().join(', ')}`)
    }
  }
}

console.log(`\n${total} error code(s) checked in ${modules.join(', ')}${changed ? ' (changed enum files only)' : ''}.`)

const duplicated = [...owners].filter(([, files]) => new Set(files).size > 1)
if (duplicated.length) {
  failed = true
  console.log('\nCodes declared by more than one enum (the message key would collide):')
  for (const [code, files] of duplicated) console.log(`  ${code}: ${[...new Set(files)].join(', ')}`)
}
if (!failed) console.log('No missing message, no duplicate code.')
process.exit(failed ? 1 : 0)
