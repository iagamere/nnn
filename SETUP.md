# خطوات الإعداد الأولى (بالترتيب)

1. أنشئ مستودعاً **خاصاً (Private)** على GitHub، ثم ارفع إليه محتويات المجلد `github-repo/` كما هي (مع المجلد المخفي `.github/workflows`).
2. في المستودع: Settings ← Actions ← General ← Workflow permissions ← اختر **Read and write permissions**.
3. أنشئ Fine-grained token: Resource owner = حسابك، Repository access = هذا المستودع فقط، الصلاحيات: **Contents: Read and write**، **Actions: Read and write** (صلاحية Releases تأتي ضمن Contents).
4. من تبويب Actions شغّل `toolchain` يدوياً **مرة واحدة** وانتظر حتى ينجح (يبني صورة Docker ويدفعها إلى ghcr.io).
5. حوّل `android-app/` إلى APK بأداتك، ثبّته، وأدخل في الإعدادات: token وowner وrepo وIP الـ PS4.
6. اختر zip المشروع (ps4-ezremote-server-abdo) وتابع البناء.

مدة toolchain: تقدير فقط (غير مُتحقَّق منه) من 20 إلى 60 دقيقة. بناء المشروع بعد ذلك: بضع دقائق.
بعد نجاح toolchain: قد تحتاج ضبط ظهور الحزمة `ps4-toolchain` (Package settings) بحيث يصل إليها المستودع، إن فشل سحب الصورة.
