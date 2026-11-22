// Ürün onayı bekleyen seçenekler, API'nin mevcut string alanına gönderilir.
export const genderOptions = [
  { value: 'Male', label: 'Male' },
  { value: 'Female', label: 'Female' },
  { value: 'Other', label: 'Other' },
  { value: 'Not disclosed', label: 'Prefer not to disclose' },
] as const

export function isSupportedGender(value: string): boolean {
  return genderOptions.some(option => option.value === value)
}

export const patientPhoneMessage = 'Enter 11 digits starting with 05, such as 0500 000 00 00.'

function formatMobileDigits(digits: string): string {
  return [digits.slice(0, 4), digits.slice(4, 7), digits.slice(7, 9), digits.slice(9)].filter(Boolean).join(' ')
}

// Tanınan eski mobil numaralar yerel görünür. Diğer eski değerleri değiştirmeyiz.
export function displayStoredTurkishMobile(raw: string): string {
  if (!/^(?:\+90[ -]?|0[ -]?)?(?:5\d{2}|\(5\d{2}\))(?:[ -]?\d){7}$/.test(raw.trim())) return raw
  return formatMobileDigits('0' + raw.replace(/\D/g, '').slice(-10))
}

// Ülke kodu yalnızca doğrulanmış yerel numaradan API isteği hazırlanırken eklenir.
export function normalizeTurkishMobile(raw: string): string | null {
  if (raw.length > 32 || /[^0-9 ]/.test(raw)) return null
  const digits = raw.replace(/ /g, '')
  return /^05[0-9]{9}$/.test(digits) ? '+90' + digits.slice(1) : null
}

// İmleci rakam sayısına göre eşleriz. Ayırıcı silinirse silme yönündeki rakamı kaldırırız.
export function editTurkishMobile(previous: string, raw: string, caret: number, inputType?: string) {
  if (/[^0-9 ]/.test(raw)) return { error: patientPhoneMessage } as const
  let digits = raw.replace(/ /g, '')
  if (digits.length > 11 || raw.length > 32) {
    return { error: 'Use at most 11 digits. The entry was not changed.' } as const
  }
  let before = raw.slice(0, caret).replace(/ /g, '').length
  if (raw.length + 1 === previous.length && digits === previous.replace(/ /g, '')) {
    if (inputType === 'deleteContentBackward' && before > 0) {
      before -= 1
      digits = digits.slice(0, before) + digits.slice(before + 1)
    } else if (inputType === 'deleteContentForward') {
      digits = digits.slice(0, before) + digits.slice(before + 1)
    }
  }
  const value = formatMobileDigits(digits)
  let position = 0
  while (before > 0 && position < value.length) {
    if (value[position] !== ' ') before -= 1
    position += 1
  }
  return { value, caret: position }
}
