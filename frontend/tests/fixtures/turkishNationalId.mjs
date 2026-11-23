// Testler yalnızca deterministik başlangıçlardan hesaplanan sentetik dizileri kullanır.
export function syntheticTurkishNationalId(prefix) {
  if (typeof prefix !== 'string' || !/^[1-9][0-9]{8}$/.test(prefix)) throw new Error('Expected a nine-digit synthetic prefix.')
  const digits = Array.from(prefix, Number)
  const weightedSum = digits.reduce((sum, digit, index) => sum + digit * (index % 2 === 0 ? 7 : -1), 0)
  // Dokuz haneli girişte en küçük ağırlıklı toplam -29 olur. 40 eklemek sonucu değiştirmeden kalanı pozitif tutar.
  const tenth = (weightedSum + 40) % 10
  const eleventh = (digits.reduce((sum, digit) => sum + digit, tenth)) % 10
  return prefix + tenth + eleventh
}
