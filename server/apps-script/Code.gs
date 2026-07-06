// Google Apps Script version of the Maliar Pro billing backend - a free, no-hosting-
// needed alternative to the Node.js server in /server. Uses PropertiesService as a tiny
// key-value store (no Google Sheet needed) and UrlFetchApp to call Zarinpal's API.
//
// SETUP:
// 1. Go to https://script.google.com/ -> New project.
// 2. Delete the default code and paste this whole file in.
// 3. In "Project Settings" (gear icon) -> Script Properties, add:
//      ZARINPAL_MERCHANT_ID = your merchant id
//      ZARINPAL_SANDBOX     = true   (set to false once you've tested)
//      PRICE_MONTHLY_RIAL   = 800000
//      PRICE_YEARLY_RIAL    = 6500000
// 4. Deploy -> New deployment -> type: "Web app".
//      Execute as: Me
//      Who has access: Anyone
// 5. Copy the resulting /exec URL - that's what goes into the Android app's
//    SubscriptionManager.kt as STATUS_URL/REQUEST_URL (see bottom of this file for the
//    exact values to use).

function getSetting_(key, fallback) {
  const value = PropertiesService.getScriptProperties().getProperty(key);
  return (value === null || value === undefined || value === '') ? fallback : value;
}

function getPlans_() {
  return {
    monthly: { days: 30, amountRial: parseInt(getSetting_('PRICE_MONTHLY_RIAL', '800000'), 10) },
    yearly: { days: 365, amountRial: parseInt(getSetting_('PRICE_YEARLY_RIAL', '6500000'), 10) }
  };
}

function isSandbox_() {
  return getSetting_('ZARINPAL_SANDBOX', 'true') === 'true';
}

function zarinpalUrls_() {
  const sandbox = isSandbox_();
  return {
    request: sandbox
      ? 'https://sandbox.zarinpal.com/pg/v4/payment/request.json'
      : 'https://api.zarinpal.com/pg/v4/payment/request.json',
    verify: sandbox
      ? 'https://sandbox.zarinpal.com/pg/v4/payment/verify.json'
      : 'https://api.zarinpal.com/pg/v4/payment/verify.json',
    startPay: sandbox
      ? 'https://sandbox.zarinpal.com/pg/StartPay/'
      : 'https://www.zarinpal.com/pg/StartPay/'
  };
}

// --- tiny device/order storage using PropertiesService ------------------------------
// Well within Apps Script's free quota (500KB total, ~9KB per value) for many thousands
// of small device/order records - fine for an app at this scale.

function getDeviceRecord_(deviceId) {
  const raw = PropertiesService.getScriptProperties().getProperty('device_' + deviceId);
  return raw ? JSON.parse(raw) : { premiumUntil: 0 };
}

function grantPremiumDays_(deviceId, days) {
  const current = getDeviceRecord_(deviceId).premiumUntil || 0;
  const base = Math.max(current, Date.now());
  const premiumUntil = base + days * 24 * 60 * 60 * 1000;
  PropertiesService.getScriptProperties().setProperty('device_' + deviceId, JSON.stringify({ premiumUntil: premiumUntil }));
  return premiumUntil;
}

function getOrder_(authority) {
  const raw = PropertiesService.getScriptProperties().getProperty('order_' + authority);
  return raw ? JSON.parse(raw) : null;
}

function saveOrder_(authority, order) {
  PropertiesService.getScriptProperties().setProperty('order_' + authority, JSON.stringify(order));
}

// --- HTTP response helpers -----------------------------------------------------------

function jsonOutput_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function htmlOutput_(message) {
  return HtmlService.createHtmlOutput(
    '<html dir="rtl" lang="fa"><body style="font-family:tahoma;text-align:center;padding:40px">' +
    message +
    '<p>می‌توانید این صفحه را ببندید و به اپ مالیار پرو برگردید.</p></body></html>'
  );
}

// --- main entry point ------------------------------------------------------------------
// Everything is a GET (Apps Script Web Apps handle POST redirects unreliably, so the
// Android client sends deviceId/plan as query params instead - see SubscriptionManager.kt).

function doGet(e) {
  const params = e.parameter || {};
  const path = params.path;

  if (path === 'status') return handleStatus_(params);
  if (path === 'request') return handleRequest_(params);
  if (path === 'callback') return handleCallback_(params);

  return jsonOutput_({ error: 'unknown_path' });
}

function handleStatus_(params) {
  const deviceId = params.deviceId;
  if (!deviceId) return jsonOutput_({ error: 'deviceId is required' });
  const record = getDeviceRecord_(deviceId);
  return jsonOutput_({ isPremium: record.premiumUntil > Date.now(), premiumUntil: record.premiumUntil });
}

function handleRequest_(params) {
  const deviceId = params.deviceId;
  const plan = params.plan;
  const planConfig = getPlans_()[plan];
  if (!deviceId || !planConfig) return jsonOutput_({ error: 'deviceId and a valid plan are required' });

  const merchantId = getSetting_('ZARINPAL_MERCHANT_ID', '');
  if (!merchantId) return jsonOutput_({ error: 'ZARINPAL_MERCHANT_ID is not configured' });

  // The web app's own /exec URL, so Zarinpal can redirect back into this same script.
  const selfUrl = ScriptApp.getService().getUrl();
  const callbackUrl = selfUrl + '?path=callback&deviceId=' + encodeURIComponent(deviceId) + '&plan=' + plan;

  const urls = zarinpalUrls_();
  const response = UrlFetchApp.fetch(urls.request, {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    payload: JSON.stringify({
      merchant_id: merchantId,
      amount: planConfig.amountRial,
      callback_url: callbackUrl,
      description: 'مالیار پرو - ' + (plan === 'monthly' ? 'اشتراک ماهانه' : 'اشتراک سالانه')
    })
  });

  const data = JSON.parse(response.getContentText());
  if (data && data.data && data.data.code === 100) {
    const authority = data.data.authority;
    saveOrder_(authority, { deviceId: deviceId, plan: plan, amountRial: planConfig.amountRial, verified: false });
    return jsonOutput_({ paymentUrl: urls.startPay + authority });
  }

  return jsonOutput_({ error: 'zarinpal_request_failed', details: data });
}

function handleCallback_(params) {
  const authority = params.Authority;
  const status = params.Status;
  const deviceId = params.deviceId;
  const plan = params.plan;
  const planConfig = getPlans_()[plan];

  if (status !== 'OK' || !authority || !deviceId || !planConfig) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>پرداخت لغو شد یا اطلاعات ناقص بود.</p>');
  }

  const order = getOrder_(authority);
  if (!order) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>این تراکنش شناخته نشده است.</p>');
  }
  if (order.verified) {
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال است.</p>');
  }

  const merchantId = getSetting_('ZARINPAL_MERCHANT_ID', '');
  const urls = zarinpalUrls_();
  const response = UrlFetchApp.fetch(urls.verify, {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    payload: JSON.stringify({ merchant_id: merchantId, amount: order.amountRial, authority: authority })
  });
  const data = JSON.parse(response.getContentText());

  // 100 = verified now, 101 = already verified before - both count as success.
  if (data && data.data && (data.data.code === 100 || data.data.code === 101)) {
    order.verified = true;
    order.refId = data.data.ref_id;
    saveOrder_(authority, order);
    grantPremiumDays_(deviceId, planConfig.days);
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال شد.</p>');
  }

  return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>تایید پرداخت توسط زرین‌پال ناموفق بود.</p>');
}
