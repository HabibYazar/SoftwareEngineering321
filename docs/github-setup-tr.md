# GitHub'a yükleme — kısa Türkçe rehber

1. GitHub'da **`hms-community`** adlı boş bir repository aç. Hak sahipliği netleşene kadar **Private** bırak. README veya LICENSE seçme; dosyalar pakette mevcut.
2. ZIP'i bilgisayarda aç. O klasörde Git Bash / terminal çalıştır.
3. Aşağıdaki komutlarla ilk push'u yap:

```bash
git init
git add .
git commit -m "chore: initialize HMS Community Sprint 1 architecture"
git branch -M main
git remote add origin https://github.com/USERNAME/hms-community.git
git push -u origin main
# Integration branch
git checkout -b develop
git push -u origin develop
```

4. Settings > Collaborators and teams kısmından ekip üyelerini ekle (gerekli davetleri onlar kabul eder).
5. Settings > Rules / Rulesets ile `main` ve `develop` için PR, en az 1 onay, `backend-tests`, `frontend-build`, `api-validation` CI kontrollerini zorunlu kıl. (Özelliklerin çalışması plan ve görünürlüğe göre değişebilir.)
6. İlk GitHub Project oluştur: Backlog → Ready → In Progress → Review → Done. Backlog'daki US-01 ... US-10 kayıtlarını Issue olarak gir.
7. Takım üyelerinin adlarını `docs/sprint-1/raci.md` içine yaz. Hocanın altıncı rol konusunda onayını al.
8. Teslimde sürüm etiketi ver: `git tag -a sprint-1 -m "Sprint 1 foundation" && git push origin sprint-1`.

**Dikkat:** Apache-2.0 lisansını yayınlamadan önce ekip katkı haklarını ve üniversite kurallarını doğrula. Açık kaynak izinlerini sonradan geçmiş dağıtımlardan geri almak mümkün değildir.
