import { createServer } from 'node:http'
import { createInterface } from 'node:readline'
import { createMockStaffService } from '../../src/staff/mockStaffService.ts'

const samples = await createMockStaffService().listClinicians(null)
const modes = new Set(['normal', 'changed', 'empty', 'error', 'catalog-error', 'removed'])
let mode = 'normal'

// Bu yerel test sunucusu sentetik yanıtları değiştirerek gerçek HTTP istemcisinin yenilenmesini sınar.
const terminal = createInterface({ input: process.stdin, output: process.stdout })
terminal.on('line', line => {
  const next = line.trim()
  if (!modes.has(next)) {
    console.log('Available modes:', [...modes].join(', '))
    return
  }
  mode = next
  console.log('Fixture mode:', mode)
})
terminal.on('SIGINT', () => process.exit(0))

function json(response, status, value) {
  if (response.destroyed) return
  response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' })
  response.end(JSON.stringify(value))
}

const server = createServer((request, response) => {
  const url = new URL(request.url, 'http://localhost')
  if (request.method !== 'GET') return json(response, 405, {})
  if (url.pathname === '/api/v1/health') return json(response, 200, { status: 'UP' })
  if (url.pathname !== '/api/v1/staff') return json(response, 404, {})

  const department = url.searchParams.get('department')
  const currentMode = mode
  const cardiology = department === 'Cardiology'
  console.log('Staff request:', department ?? 'all', currentMode)

  // Gecikme sayesinde tarayıcıda yükleme durumu ve eski isteğin iptali gözlemlenebilir.
  setTimeout(() => {
    if ((cardiology && currentMode === 'error') || (!department && currentMode === 'catalog-error')) {
      return json(response, 503, { detail: 'Synthetic test failure' })
    }
    if (cardiology && currentMode === 'empty') return json(response, 200, [])

    const list = samples.filter(doctor =>
      (!department || doctor.department === department)
      && (currentMode !== 'removed' || doctor.department !== 'Cardiology'),
    )
    if (currentMode === 'changed') {
      json(response, 200, list.map(doctor => doctor.id === samples[0].id
        ? { ...doctor, displayName: 'Dr. Avery Rowan Updated' } : doctor))
      return
    }
    json(response, 200, list)
  }, 450)
})

server.listen(8080, 'localhost', () => {
  console.log('Synthetic fixture at http://localhost:8080. Enter a mode:', [...modes].join(', '))
})
