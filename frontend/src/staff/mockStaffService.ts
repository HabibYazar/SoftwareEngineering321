import type { Clinician, StaffService } from './types.ts'

// Eğitim için uydurulmuş adlar ve kimlikler, canlı personel verisi içermez.
const demoClinicians: Clinician[] = [
  { id: '00000000-0000-4000-8000-000000000001', displayName: 'Dr. Avery Rowan', department: 'Cardiology' },
  { id: '00000000-0000-4000-8000-000000000002', displayName: 'Dr. Ellis Vale', department: 'Cardiology' },
  { id: '00000000-0000-4000-8000-000000000003', displayName: 'Dr. Maya Finch', department: 'Dermatology' },
  { id: '00000000-0000-4000-8000-000000000004', displayName: 'Dr. Riley Brooks', department: 'Neurology' },
  { id: '00000000-0000-4000-8000-000000000005', displayName: 'Dr. Noor Linden', department: 'Neurology' },
  { id: '00000000-0000-4000-8000-000000000006', displayName: 'Dr. Morgan Reed', department: 'Orthopedics' },
  { id: '00000000-0000-4000-8000-000000000007', displayName: 'Dr. Jamie Birch', department: 'Pediatrics' },
  { id: '00000000-0000-4000-8000-000000000008', displayName: 'Dr. Sam Wren', department: 'Pediatrics' },
]

// Demo servisi, HTTP servisiyle aynı departman eşleştirmesini ve boş listeyi sunar.
export function createMockStaffService(): StaffService {
  return {
    async listClinicians(department, signal) {
      signal?.throwIfAborted()
      return demoClinicians.filter(clinician => department === null || clinician.department === department)
    },
  }
}
