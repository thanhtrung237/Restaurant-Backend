// Two local Compose projects: restaurant-service (BE + MySQL), restaurant-client (FE).
const fs = require('node:fs')
const path = require('node:path')
const cp = require('node:child_process')
const assert = require('node:assert/strict')
const root = path.resolve(__dirname, '..')
const frontendRoot = path.resolve(root, '../Restaurant-Frontend')
const runtime = path.join(root, '.docker-runtime/compose-groups')
process.chdir(root)
const args = process.argv.slice(2)
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback
const service = option('--service', 'all')
const wait = option('--wait', '600')
const environment = path.resolve(option('--env', '.env.docker'))
const be = ['compose', '--env-file', environment, '-f', path.join(root, 'compose.yaml')]
const fe = ['compose', '--env-file', environment, '-f', path.join(frontendRoot, 'compose.yaml')]
function docker(command, options = {}) {
  const result = cp.spawnSync('docker', command, {
    encoding: 'utf8', maxBuffer: 32 * 1024 * 1024, windowsHide: true, ...options,
  })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`Docker ${command[0]} failed: ${result.stderr || 'see output above'}`)
  return (result.stdout || '').trim()
}
function inspect(name) { return JSON.parse(docker(['inspect', name]))[0] }
function hasImage(name) {
  return cp.spawnSync('docker', ['image', 'inspect', name], {stdio: 'ignore', windowsHide: true}).status === 0
}
function query(container, sql) {
  return docker(['exec', '-i', container, 'sh', '-ec',
    'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --protocol=TCP -h 127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names'], {input: sql})
}
function counts(container) {
  return Object.fromEntries(query(container, 'SHOW TABLES;\n').split(/\r?\n/).filter(Boolean).map(table => {
    const identifier = '`' + table.replaceAll('`', '``') + '`'
    return [table, Number(query(container, `SELECT COUNT(*) FROM ${identifier};\n`))]
  }))
}
function backup(container) {
  fs.mkdirSync(runtime, {recursive: true})
  const target = path.join(runtime, `before-groups-${new Date().toISOString().replaceAll(/[:.]/g, '-')}.sql`)
  const fd = fs.openSync(target, 'wx', 0o600)
  try {
    docker(['exec', container, 'sh', '-ec',
      'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysqldump --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --column-statistics=0 --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"'],
      {stdio: ['ignore', fd, 'pipe']})
  } finally { fs.closeSync(fd) }
  if (!fs.statSync(target).size) throw new Error('Database backup is empty.')
  console.log('Local backup: ' + target)
  return target
}
async function waitHealthy(id) {
  const deadline = Date.now() + Number(wait) * 1000
  while (Date.now() < deadline) {
    const state = inspect(id).State
    if (state.Health?.Status === 'healthy') return
    if (!state.Running) throw new Error('Container stopped during recovery.')
    await new Promise(resolve => setTimeout(resolve, 2000))
  }
  throw new Error('Recovery healthcheck timed out.')
}
async function main() {
  if (!['all', 'backend', 'mysql', 'frontend'].includes(service)) throw new Error('Unknown service.')
  docker(['info', '--format', '{{.ServerVersion}}'])
  const config = JSON.parse(docker([...be, 'config', '--format', 'json']))
  const feConfig = JSON.parse(docker([...fe, 'config', '--format', 'json']))
  if (config.services.mysql.environment.MYSQL_USER === 'root') throw new Error('MYSQL_USER must be an application user.')
  const volume = config.volumes.mysql_data.name
  const network = config.networks.default.name
  assert.equal(network, feConfig.networks.default.name, 'FE and BE must use the same external network.')
  const all = docker(['ps', '-a', '--format', '{{.Names}}']).split(/\r?\n/).filter(Boolean).map(inspect)
  const legacy = c => ['restaurant-two', 'restaurant-local'].includes(c.Config.Labels?.['com.docker.compose.project']) ||
    c.Config.Labels?.['com.restaurant.stack'] === 'kltn-standalone'
  const old = all.filter(legacy)
  const writers = all.filter(c => c.State.Running && c.Mounts.some(m => m.Type === 'volume' && m.Name === volume))
  if (writers.length > 1) throw new Error('Multiple containers are writing the database volume.')
  const source = writers[0]
  if (source && !legacy(source) && source.Config.Labels?.['com.docker.compose.project'] !== 'restaurant-service') {
    throw new Error('An unrelated container uses this database volume; nothing was stopped.')
  }
  if (source) {
    const originalMysql = source.Config.Labels?.['com.restaurant.mysql-base'] || source.Image
    const baseId = JSON.parse(docker(['image', 'inspect', originalMysql]))[0].Id
    const targetId = JSON.parse(docker(['image', 'inspect', config.services.mysql.image]))[0].Id
    assert.equal(targetId, baseId, 'MYSQL_IMAGE must match the current MySQL version before moving its volume.')
  }
  const migrating = old.length > 0
  const startBe = service !== 'frontend' || migrating
  const startFe = ['all', 'frontend'].includes(service) || migrating
  if (!hasImage(config.services.mysql.image)) docker(['pull', config.services.mysql.image], {stdio: 'inherit'})
  if (startBe && service !== 'mysql' && (args.includes('--build') || !hasImage(config.services.backend.image))) {
    docker([...be, 'build', 'backend'], {stdio: 'inherit'})
  }
  if (startFe && (args.includes('--build') || !hasImage(feConfig.services.frontend.image))) {
    docker([...fe, 'build', 'frontend'], {stdio: 'inherit'})
  }
  // All builds finish before any existing application is stopped.
  docker(['volume', 'create', volume])
  const networkExists = cp.spawnSync('docker', ['network', 'inspect', network], {stdio: 'ignore', windowsHide: true}).status === 0
  if (!networkExists) docker(['network', 'create', network])
  let stoppedOld = false
  try {
    let before
    if (source && migrating) {
      const apps = old.filter(c => c.State.Running && c.Id !== source.Id)
      if (apps.length) {
        stoppedOld = true
        docker(['stop', '--timeout', '90', ...apps.map(c => c.Id)], {stdio: 'inherit'})
      }
      const backupPath = backup(source.Id)
      before = counts(source.Id)
      fs.writeFileSync(path.join(runtime, 'last-switch.json'), JSON.stringify({backupPath, counts: before, volume}, null, 2))
      stoppedOld = true
      docker(['stop', '--timeout', '90', source.Id], {stdio: 'inherit'})
    }
    const up = ['up', '-d', '--no-build', '--pull', 'never', '--wait', '--wait-timeout', wait]
    if (startBe) {
      docker([...be, ...up, ...(service === 'mysql' && args.includes('--recreate') ? ['--force-recreate'] : []), 'mysql'], {stdio: 'inherit'})
      if (service !== 'mysql' || migrating) {
        docker([...be, ...up, '--no-deps', ...(args.includes('--recreate') ? ['--force-recreate'] : []), 'backend'], {stdio: 'inherit'})
      }
    }
    if (startFe) docker([...fe, ...up, ...(args.includes('--recreate') ? ['--force-recreate'] : [])], {stdio: 'inherit'})
    if (before) {
      const after = counts(docker([...be, 'ps', '-q', 'mysql']))
      assert.deepEqual(after, before, 'Database row counts changed during the move.')
      console.log(`Database preserved: ${Object.keys(after).length} tables, ${Object.values(after).reduce((a,b) => a+b, 0)} rows.`)
    }
    if (old.length) docker(['rm', ...old.map(c => c.Id)], {stdio: 'inherit'})
    docker([...be, 'ps'], {stdio: 'inherit'})
    docker([...fe, 'ps'], {stdio: 'inherit'})
    console.log('Ready: restaurant-service (backend + mysql), restaurant-client (frontend). EC2 unchanged.')
  } catch (error) {
    if (stoppedOld) {
      console.error('Move failed. Restoring the original local containers...')
      try {
        docker([...fe, 'stop'], {stdio: 'inherit'})
        docker([...be, 'stop'], {stdio: 'inherit'})
        docker(['start', source.Id])
        await waitHealthy(source.Id)
        const apps = old.filter(c => c.State.Running && c.Id !== source.Id)
        if (apps.length) docker(['start', ...apps.map(c => c.Id)])
      } catch (restoreError) { console.error('Recovery failed: ' + restoreError.message) }
    }
    throw error
  }
}
main().catch(error => {console.error(error.message); process.exitCode = 1})
