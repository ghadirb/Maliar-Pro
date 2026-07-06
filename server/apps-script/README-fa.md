# نسخه‌ی Google Apps Script (کاملاً رایگان، بدون سرور واقعی)

این جایگزینِ رایگانِ پوشه‌ی `/server` است - همان سه کار را انجام می‌دهد ولی روی
زیرساخت گوگل اجرا می‌شود؛ نه پولی است، نه نیاز به CLI/دیپلوی دارد، و معمولاً از ایران
هم بدون مشکل فیلترینگ در دسترس است (چون درخواست از گوشی کاربر ایرانی به سمت گوگل
می‌رود، نه برعکس - شبیه استفاده از جیمیل یا گوگل‌درایو).

## مراحل نصب
1. به [script.google.com](https://script.google.com) بروید → پروژه‌ی جدید.
2. کد `Code.gs` را کامل کپی و جایگزین کد پیش‌فرض کنید.
3. از آیکون چرخ‌دنده (Project Settings) → پایین صفحه → "Script Properties" → Add script
   property، این مقادیر را اضافه کنید:
   - `ZARINPAL_MERCHANT_ID` = مرچنت آی‌دی شما (یا مرچنت sandbox برای تست)
   - `ZARINPAL_SANDBOX` = `true` (اول برای تست، بعداً `false`)
   - `PRICE_MONTHLY_RIAL` = `800000` (به ریال)
   - `PRICE_YEARLY_RIAL` = `6500000`
4. دکمه‌ی آبی «Deploy» بالا سمت راست → «New deployment» → روی چرخ‌دنده‌ی کنار
   «Select type» بزنید → «Web app» را انتخاب کنید.
   - Execute as: **Me**
   - Who has access: **Anyone**
   - «Deploy» را بزنید و اجازه‌ی دسترسی (Authorize access) را با اکانت گوگل خودتان تایید کنید.
5. یک آدرس مثل این به شما داده می‌شود:
   `https://script.google.com/macros/s/AKfycb.../exec`
   این را کپی کنید.

## وصل کردن به اپ اندروید
در فایل `app/src/main/java/com/maliar/pro/utils/SubscriptionManager.kt`:
```kotlin
const val STATUS_URL = "https://script.google.com/macros/s/AKfycb.../exec?path=status"
const val REQUEST_URL = "https://script.google.com/macros/s/AKfycb.../exec?path=request"
```
(همان آدرس exec، فقط با `?path=status` و `?path=request` در انتهایش) و اپ را rebuild کنید.

## تست
دقیقاً مثل نسخه‌ی Node - ابتدا با `ZARINPAL_SANDBOX=true` امتحان کنید، بعد از تایید صحت
عملکرد، `ZARINPAL_SANDBOX` را `false` و مرچنت واقعی را جایگزین کنید.

## محدودیت‌ها (برای مقیاس آینده)
- ذخیره‌سازی با `PropertiesService` است، نه یک دیتابیس واقعی - برای صدها/چندهزار کاربر
  کاملاً کافی است؛ اگر خیلی بزرگ شدید، همین ساختار قابل جایگزینی با یک Google Sheet یا
  دیتابیس واقعی است.
- هر بار که کد را در Apps Script ویرایش می‌کنید، باید از «Deploy» → «Manage deployments»
  → مداد ویرایش → نسخه‌ی جدید را «Deploy» کنید تا تغییرات روی آدرس exec اعمال شوند.
