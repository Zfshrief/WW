# طريقة الحصول على ملف الـ APK (من غير Android Studio)

الفكرة: ترفع المشروع على GitHub، وهو يبني الـ APK لوحده مجاناً، وإنت تنزله من هناك.

## الخطوات
1. اعمل حساب مجاني على https://github.com
2. اضغط **New repository** وسمّيه أي اسم (مثلاً `contacts-widget`) واختار **Public**.
3. افتح الصفحة الفاضية واضغط **uploading an existing file**، وفك الضغط عن الملف `FreeContactsWidget.zip` على جهازك، واسحب كل اللي جواه مباشرة (الفولدر `app` والملفات `build.gradle.kts` و`settings.gradle.kts` و`gradle.properties`) واضغط **Commit changes**.
   - لو فولدر `.github` ماظهرش بعد الرفع: اضغط **Add file > Create new file** واكتب الاسم `.github/workflows/build.yml` والصق محتوى الملف `build-workflow.yml.txt` اللي في الـ zip، واضغط Commit.
4. افتح تبويب **Actions** وانتظر 4 إلى 6 دقايق لحد ما يظهر علامة صح خضراء.
5. اضغط على آخر عملية، ونزّل من تحت **Artifacts** الملف `FreeContactsWidget-apk`، فك الضغط وهتلاقي `app-debug.apk`.
6. انقله للموبايل وثبّته (فعّل «التثبيت من مصادر غير معروفة» لو طلب).

## لو ظهرت علامة حمراء في Actions
افتح العملية وانسخ آخر 30 سطر من سجل الخطأ وابعتهم لي، وأصلحها لك.

## الاستخدام
اضغط مطولاً على الشاشة الرئيسية، ثم ويدجت، ثم «ويدجت جهات الاتصال».
