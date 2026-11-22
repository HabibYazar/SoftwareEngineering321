type Errors<Field extends string> = Partial<Record<Field, string>>

// Sunucu hatası, kullanıcı alanı gerçekten değiştirene kadar yerel hataya göre önceliklidir.
export function visibleFieldErrors<Field extends string>(
  client: Errors<Field>, server: Errors<Field>, touched: ReadonlySet<Field>, submitted: boolean,
): Errors<Field> {
  if (submitted) return { ...client, ...server }
  const visible = { ...server }
  for (const field of touched) {
    const error = client[field]
    if (error && !visible[field]) visible[field] = error
  }
  return visible
}
