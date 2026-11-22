# Obsidian Night bağımlılık envanteri

Doğrudan paketler `frontend/package.json`, kesin sürümler `frontend/package-lock.json` ve kullanım alanları mevcut kaynak içe aktarımları ile proje belgelerinden doğrulandı.

| Paket | Kilit sürümü | Tür | Amaç ve mevcut kullanım | Önceden var mı? | Yerel katkı kanıtı |
| --- | --- | --- | --- | --- | --- |
| react | 19.3.1 | Üretim | JSX bileşenleri, tema ve oturum dahil mevcut React hook ve context kullanımları. | Evet | Habib Yazar, `75ab93a` manifest kaydı. |
| react-dom | 19.3.1 | Üretim | `react-dom/client` ile uygulamayı DOM’a bağlar. Bazı mevcut testler `react-dom/server` kullanır. | Evet | Habib Yazar, `75ab93a` manifest kaydı. |
| @types/react | 19.3.0 | Geliştirme | React, JSX ve olay nesneleri için TypeScript tipleri. | Evet | Habib Yazar, `75ab93a` manifest kaydı. |
| @types/react-dom | 19.3.0 | Geliştirme | React DOM giriş noktalarının TypeScript tipleri. | Evet | Habib Yazar, `75ab93a` manifest kaydı. |
| @vitejs/plugin-react | 5.2.0 | Geliştirme | `vite.config.ts` üzerinden React dönüşümü ve geliştirme yenilemesi. | Evet | Habib Yazar, `75ab93a` manifest kaydı. |
| typescript | 5.9.3 | Geliştirme | `tsc -b` ile mevcut TS ve TSX kaynaklarını denetler. | Evet | Habib Yazar, `75ab93a` manifest kaydı. |
| vite | 7.3.8 | Geliştirme | Ön yüz geliştirme sunucusu, yerel önizleme ve üretim çıktısı. | Evet | Habib Yazar, `75ab93a` manifest kaydı. |

Ön yüz için önemli geçişli paketler de mevcut kilit dosyasından doğrulandı. Platforma özgü ve diğer geçişli paketlerin tam listesi kilit dosyasındadır.

| Paket | Kilit sürümü | Tür | Mevcut kullanım | Önceden var mı? | Teammate atfı |
| --- | --- | --- | --- | --- | --- |
| scheduler | 0.28.0 | Üretim, geçişli | React DOM içindeki iş zamanlama altyapısı. | Evet | UNKNOWN |
| esbuild | 0.28.2 | Geliştirme, geçişli | Vite’ın hızlı kaynak dönüşümü. | Evet | UNKNOWN |
| rollup | 4.68.7 | Geliştirme, geçişli | Vite üretim paketlemesi. | Evet | UNKNOWN |
| postcss | 8.5.33 | Geliştirme, geçişli | Vite’ın CSS işleme zinciri. | Evet | UNKNOWN |
| @babel/core | 7.29.13 | Geliştirme, geçişli | React Vite eklentisinin dönüşüm araçları. | Evet | UNKNOWN |

`75ab93a` manifestteki kayıtların Habib Yazar imzalı geçmiş kanıtıdır. Kilit dosyasının `6c2ec00` kaydı ayrı bir entegrasyon commit’idir. Kesin sürümlerin tek tek Habib ya da Yılmaz tarafından seçildiği doğrulanamadı. Yılmaz’a ait doğrulanmış ön yüz paket atfı UNKNOWN.

Obsidian Night mevcut React bileşenleri ve CSS ile uygulanmıştır. Manifest ve kilit dosyası değişmedi. Test ortamında Premium-UI yerel paketlerinin kilit sürümleri doğrulandı ve yalnızca Obsidian-Night içindeki izlenmeyen `frontend/node_modules/` dizinine kopyalandı. Paket yöneticisiyle kurulum yapılmadı.

**Güncelleme kuralı.** Gelecekte onaylanan her ekleme, kaldırma veya sürüm değişikliğinde bu envanter, manifest ve kilit dosyasıyla birlikte açıkça güncellenmelidir. Yeni kullanım alanı ve güvenilir katkı kanıtı da kayda girer.

[Mevcut teknoloji belgeleri](../TECH_STACK.md) · [Manifest](../../package.json) · [Kilit dosyası](../../package-lock.json)
