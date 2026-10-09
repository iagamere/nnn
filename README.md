# PS4 Payload Builder (Android + GitHub Actions)

واجهة Android عربية/إنجليزية لبناء `ezremote-server-<version>.elf` عبر GitHub Actions، تنزيل سجلات البناء، ثم إرسال ELF إلى GoldHEN BinLoader على `IP:9090` والتحقق من `http://IP:6701/v2/info`.

> **تنبيه صريح:** هذا المشروع scaffold قابل للتعديل وليس بناءً تم اختباره. لم يُرفق مشروع C++ الحقيقي، ولم يُنفّذ بناء Docker/PS4 أو اختبار جهاز. وصفة بعض مكتبات OpenOrbis تختلف بمرور الوقت؛ لذلك يفشل workflow بصوت واضح بدل تخمين إصلاحات. لا تضع token في ملفات المصدر أو سجلات Actions.

## 1. ارفع المشروع إلى مستودع خاص
1. أنشئ GitHub repository خاصاً جديداً.
2. ارفع محتويات هذا المجلد إلى جذر المستودع.
3. أنشئ Fine-grained PAT للمستودع: **Contents: Read and write** و **Actions: Read and write**. إنشاء/قراءة Releases تتم عبر Contents API؛ لا توجد صلاحية مستقلة باسم Releases في واجهة fine-grained المعتادة. أضف Metadata: Read تلقائياً إن طلبها GitHub.
4. من Settings → Actions → General اسمح للـ workflow بإنشاء وإدارة محتوى المستودع. GHCR يُدفع باستخدام `GITHUB_TOKEN` وصلاحية `packages: write`.
5. شغّل Actions → **Build PS4 toolchain image** → Run workflow مرة واحدة. هذه المرحلة قد تستغرق عشرات الدقائق إلى عدة ساعات حسب runner والشبكة؛ لا يمكن ضمان وقت دون تنفيذ فعلي. أعدها فقط عند تغيير الوصفة.
6. بعد نجاحها، ضع ملف المشروع C++ داخل `incoming/` كـ ZIP عبر التطبيق أو ارفع `incoming/example.zip` يدوياً. يجب أن يحتوي ZIP على مجلد فيه `CMakeLists.txt`، مع كود المشروع داخله.
7. شغّل **Build PS4 ELF** أو ادفع ZIP إلى `incoming/*.zip`.

## 2. مخرجات البناء
- نجاح: Release `build-<run_number>` وفيه ELF و`build.log` و`status.json`.
- فشل: Artifact باسم `build-diagnostics-<run_number>` يحتوي `build.log` و`errors.txt` و`status.json`. لا يمكن ضمان إنشاء Release في كل فشل إذا تعطلت صلاحيات GitHub نفسها.
- `status.json` يتضمن `ok`, `version`, `elf`, `time`.

## 3. تطبيق Android
افتح مجلد `android/` في Android Studio حديث. المشروع يستخدم Gradle Kotlin DSL، Compose، minSdk 26 وtargetSdk 34. استخدم `./gradlew assembleDebug` بعد توفير Gradle wrapper كامل من Android Studio إذا كانت بيئتك لا تتضمن wrapper JAR. **wrapper JAR غير مضمن هنا** لأن هذا الأرشيف لا يستطيع التحقق من تنزيل binary موثوق به؛ لذلك لا أصف الأرشيف بأنه APK جاهز أو بناء قابل للاختبار فوراً.

في التطبيق أدخل owner/repo وPAT وعنوان IP. يتم تخزين PAT في EncryptedSharedPreferences، ولا يُكتب في Logcat. التطبيق يرفع ZIP إلى `incoming/<timestamp>-<name>.zip` عبر GitHub Contents API، ويتابع أحدث workflow run، ويعرض diagnostics. الإلغاء يطلب إلغاء GitHub Actions run؛ لا يوقف فوراً عملية Docker إن لم يستجب GitHub.

## 4. ما لم يتم التحقق منه
- لم يُبنَ Docker image ولم تُجرّب أوامر pacbrew على runner حقيقي.
- لم يُبنَ مشروع C++ المفقود؛ لا يمكن التأكد من أسماء الأهداف أو `kernel_sys` أو أسماء المكتبات الفعلية دون ZIP المصدر وCMakeLists.
- لم يُختبر إرسال ELF على PS4 فعلي. الكود يرسل الملف الخام إلى TCP 9090 كما طلبت؛ توافقه يعتمد على BinLoader/GoldHEN الموجودين لديك.
- لم يُختبر endpoint `/v2/info` أو شكل JSON على جهازك؛ التطبيق يتوقع حقل `version`.
- وقت بناء toolchain تقديري فقط.

## 5. الملفات
- `.github/workflows/toolchain.yml`: بناء ودفع صورة الأدوات إلى GHCR.
- `.github/workflows/build.yml`: بناء ZIP، إنشاء Release أو artifact، وتوليد السجلات.
- `toolchain/Dockerfile` و`toolchain/build-deps.sh`: وصفة بناء الأدوات والمكتبات.
- `android/`: مشروع Kotlin + Compose.
