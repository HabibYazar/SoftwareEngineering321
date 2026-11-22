import type { Patient } from './types.ts'

// Arama kısayolu için gerekli olan özet alanları tutarız. Kimlik numarası ve iletişim bilgisi bu listeye girmez.
export type PatientSummary = Pick<Patient, 'id' | 'firstName' | 'lastName' | 'conditionLevel'>

export function summarizePatient({ id, firstName, lastName, conditionLevel }: Patient): PatientSummary {
  return { id, firstName, lastName, conditionLevel }
}

export function appendRecentPatient(patients: PatientSummary[], patient: Patient): PatientSummary[] {
  return [summarizePatient(patient), ...patients.filter(item => item.id !== patient.id)].slice(0, 12)
}

export function updateRecentPatient(patients: PatientSummary[], patient: Patient): PatientSummary[] {
  return patients.map(item => item.id === patient.id ? summarizePatient(patient) : item)
}
