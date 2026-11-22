// API, klinisyenleri UUID, görünen ad ve departman adı içeren bir dizi olarak döndürür.
export interface Clinician {
  id: string
  displayName: string
  department: string
}

// Arayüz aynı sözleşmeyle HTTP servisini veya açıkça seçilen demo servisini kullanır.
export interface StaffService {
  listClinicians(department: string | null, signal?: AbortSignal): Promise<Clinician[]>
}

const messages = {
  access: 'You do not have access to the doctor directory.',
  unavailable: 'The doctor directory is unavailable. This backend release does not provide the staff endpoint.',
  server: 'The doctor directory is temporarily unavailable.',
  response: 'The doctor directory returned an unexpected response.',
  network: 'Unable to reach the doctor directory. Check your connection.',
}

export class StaffError extends Error {
  readonly code: keyof typeof messages

  // Sabit mesajlar, sunucunun ham hata içeriğinin arayüzde gösterilmesini önler.
  constructor(code: keyof typeof messages) {
    super(messages[code])
    this.name = 'StaffError'
    this.code = code
  }
}
