import type { Clinician } from './types.ts'

// API sadece klinisyen kaydı döndürür. Seçenekler bu kayıtlardaki benzersiz departmanlardan oluşur.
export function listDepartments(clinicians: Clinician[]): string[] {
  return Array.from(new Set(clinicians.map(clinician => clinician.department)))
    .sort((first, second) => first.localeCompare(second))
}

// Arama, aksanlar ve Türkçe noktasız i dâhil olmak üzere adların yazım farklarını tolere eder.
function searchText(value: string): string {
  return value.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').replace(/ı/g, 'i')
}

// Filtreli HTTP yanıtında yanlışlıkla gelen diğer departman kayıtlarını da arayüzden çıkarır.
export function filterClinicians(clinicians: Clinician[], department: string | null, query: string): Clinician[] {
  const search = searchText(query.trim())
  return clinicians.filter(clinician =>
    (department === null || clinician.department === department)
    && searchText(clinician.displayName).includes(search),
  )
}
