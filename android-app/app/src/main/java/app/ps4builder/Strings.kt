package app.ps4builder

class S(private val ar: Boolean) {
    private fun t(a: String, e: String) = if (ar) a else e
    val tabBuild get() = t("بناء", "Build")
    val tabHistory get() = t("السجل", "History")
    val tabSettings get() = t("الإعدادات", "Settings")
    val pickZip get() = t("اختر zip", "Choose zip")
    val cancel get() = t("إلغاء", "Cancel")
    val cancelled get() = t("أُلغي البناء.", "Build cancelled.")
    val reading get() = t("قراءة الملف…", "Reading file…")
    val uploading get() = t("رفع الملف إلى المستودع…", "Uploading to repository…")
    val waitingRun get() = t("بانتظار بدء التشغيل…", "Waiting for the run to start…")
    fun running(step: String) = t("المرحلة: $step", "Step: $step")
    val noRunFound get() = t("لم يظهر تشغيل build.yml بعد الرفع (90 ثانية). تحقق من تبويب Actions وصلاحيات الـ token.", "No build.yml run appeared after upload (90s). Check the Actions tab and token permissions.")
    fun noRelease(c: String?) = t("انتهى التشغيل (النتيجة: $c) دون Release. غالباً الصورة غير موجودة: شغّل toolchain.yml أولاً، أو راجع تبويب Actions.", "Run ended ($c) without a release. The toolchain image may be missing: run toolchain.yml first, or check the Actions tab.")
    val fillSettings get() = t("أكمل الإعدادات (token / owner / repo) أولاً.", "Fill in settings (token / owner / repo) first.")
    val fillIp get() = t("أدخل IP الـ PS4 في الإعدادات.", "Enter the PS4 IP in settings.")
    val success get() = t("نجح البناء", "Build succeeded")
    val failed get() = t("فشل البناء", "Build failed")
    val version get() = t("الإصدار", "Version")
    val size get() = t("الحجم", "Size")
    val sendPs4 get() = t("أرسل إلى PS4", "Send to PS4")
    val copyLog get() = t("انسخ السجل", "Copy log")
    val logCopied get() = t("نُسخ errors.txt وآخر 200 سطر من build.log.", "Copied errors.txt + last 200 lines of build.log.")
    val viewLog get() = t("عرض build.log كاملاً", "View full build.log")
    val close get() = t("إغلاق", "Close")
    val noHistory get() = t("لا توجد بناءات سابقة.", "No previous builds.")
    val resend get() = t("إعادة الإرسال", "Resend")
    fun sending(ip: String) = t("جارٍ الإرسال إلى $ip:${Ps4.LOADER_PORT}…", "Sending to $ip:${Ps4.LOADER_PORT}…")
    val checking get() = t("تم الإرسال. فحص السيرفر بعد 3 ثوانٍ…", "Sent. Checking server in 3s…")
    fun serverOk(v: String) = t("السيرفر يعمل — الإصدار: $v", "Server is up — version: $v")
    fun serverNo(r: String) = t("السيرفر لم يستجب على المنفذ ${Ps4.INFO_PORT}: $r", "Server did not respond on port ${Ps4.INFO_PORT}: $r")
    fun sendFailed(r: String) = t("فشل الإرسال: $r", "Send failed: $r")
    val elfMissing get() = t("ملف ELF غير موجود على الجهاز.", "ELF file not found on device.")
    val token get() = "GitHub token"
    val owner get() = "Owner"
    val repo get() = "Repo"
    val ip get() = t("IP الـ PS4", "PS4 IP")
    val language get() = t("اللغة", "Language")
    val save get() = t("حفظ", "Save")
    val saved get() = t("تم الحفظ.", "Saved.")
    val errorsTitle get() = "errors.txt"
}
