// Minimal Zarinpal payment + subscription-status backend for Maliar Pro.
// Storage is a single JSON file (db.json) - completely fine for hundreds/low-thousands
// of users; if you outgrow it later, swap the four functions at the top for real
// database calls (Postgres, MongoDB, etc.) without touching the routes below.

const express = require('express');
const fs = require('fs');
const path = require('path');

const app = express();
app.use(express.json());

const DB_PATH = path.join(__dirname, 'db.json');

const MERCHANT_ID = process.env.ZARINPAL_MERCHANT_ID || '';
const SANDBOX = (process.env.ZARINPAL_SANDBOX || 'true') === 'true';
const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL || 'http://localhost:3000';
const PORT = process.env.PORT || 3000;

const PLANS = {
  monthly: { days: 30, amountRial: parseInt(process.env.PRICE_MONTHLY_RIAL || '800000', 10) },
  yearly: { days: 365, amountRial: parseInt(process.env.PRICE_YEARLY_RIAL || '6500000', 10) }
};

const ZARINPAL_REQUEST_URL = SANDBOX
  ? 'https://sandbox.zarinpal.com/pg/v4/payment/request.json'
  : 'https://api.zarinpal.com/pg/v4/payment/request.json';
const ZARINPAL_VERIFY_URL = SANDBOX
  ? 'https://sandbox.zarinpal.com/pg/v4/payment/verify.json'
  : 'https://api.zarinpal.com/pg/v4/payment/verify.json';
const ZARINPAL_STARTPAY_URL = SANDBOX
  ? 'https://sandbox.zarinpal.com/pg/StartPay/'
  : 'https://www.zarinpal.com/pg/StartPay/';

// --- tiny JSON "database" -----------------------------------------------------------

function loadDb() {
  if (!fs.existsSync(DB_PATH)) {
    return { devices: {}, orders: {} };
  }
  try {
    return JSON.parse(fs.readFileSync(DB_PATH, 'utf8'));
  } catch (e) {
    console.error('db.json is corrupt, starting fresh:', e.message);
    return { devices: {}, orders: {} };
  }
}

function saveDb(db) {
  fs.writeFileSync(DB_PATH, JSON.stringify(db, null, 2));
}

function getPremiumUntil(deviceId) {
  const db = loadDb();
  return (db.devices[deviceId] && db.devices[deviceId].premiumUntil) || 0;
}

/** Extends (never shortens) the device's premium expiry by `days` from whichever is
 *  later: now, or their current expiry - so buying more time while already premium
 *  stacks on top instead of wasting the remaining days. */
function grantPremiumDays(deviceId, days) {
  const db = loadDb();
  const current = (db.devices[deviceId] && db.devices[deviceId].premiumUntil) || 0;
  const base = Math.max(current, Date.now());
  const premiumUntil = base + days * 24 * 60 * 60 * 1000;
  db.devices[deviceId] = { premiumUntil };
  saveDb(db);
  return premiumUntil;
}

// --- routes --------------------------------------------------------------------------

app.get('/', (req, res) => res.send('Maliar Pro billing backend is running.'));

app.get('/subscription/status', (req, res) => {
  const deviceId = String(req.query.deviceId || '');
  if (!deviceId) return res.status(400).json({ error: 'deviceId is required' });
  const premiumUntil = getPremiumUntil(deviceId);
  res.json({ isPremium: premiumUntil > Date.now(), premiumUntil });
});

app.post('/payment/request', async (req, res) => {
  try {
    const { deviceId, plan } = req.body || {};
    const planConfig = PLANS[plan];
    if (!deviceId || !planConfig) {
      return res.status(400).json({ error: 'deviceId and a valid plan are required' });
    }
    if (!MERCHANT_ID) {
      return res.status(500).json({ error: 'ZARINPAL_MERCHANT_ID is not configured on the server' });
    }

    const callbackUrl = `${PUBLIC_BASE_URL}/payment/callback?deviceId=${encodeURIComponent(deviceId)}&plan=${plan}`;

    const zpResponse = await fetch(ZARINPAL_REQUEST_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: MERCHANT_ID,
        amount: planConfig.amountRial,
        callback_url: callbackUrl,
        description: `مالیار پرو - ${plan === 'monthly' ? 'اشتراک ماهانه' : 'اشتراک سالانه'}`
      })
    });
    const zpData = await zpResponse.json();

    if (zpData && zpData.data && zpData.data.code === 100) {
      const authority = zpData.data.authority;
      const db = loadDb();
      db.orders[authority] = { deviceId, plan, amountRial: planConfig.amountRial, verified: false };
      saveDb(db);
      return res.json({ paymentUrl: `${ZARINPAL_STARTPAY_URL}${authority}` });
    }

    console.error('Zarinpal request.json error:', JSON.stringify(zpData));
    res.status(502).json({ error: 'zarinpal_request_failed', details: zpData });
  } catch (e) {
    console.error('POST /payment/request failed:', e);
    res.status(500).json({ error: 'internal_error' });
  }
});

app.get('/payment/callback', async (req, res) => {
  const { Authority, Status, deviceId, plan } = req.query;
  const planConfig = PLANS[plan];

  const fail = (message) => res.status(200).send(`
    <html dir="rtl" lang="fa"><body style="font-family:tahoma;text-align:center;padding:40px">
    <h2>❌ پرداخت ناموفق</h2><p>${message}</p>
    <p>می‌توانید این صفحه را ببندید و به اپ مالیار پرو برگردید.</p>
    </body></html>`);

  if (Status !== 'OK' || !Authority || !deviceId || !planConfig) {
    return fail('پرداخت توسط شما لغو شد یا اطلاعات ناقص بود.');
  }

  try {
    const db = loadDb();
    const order = db.orders[Authority];
    if (!order) return fail('این تراکنش شناخته نشده است.');
    if (order.verified) {
      return res.send(successHtml());
    }

    const zpResponse = await fetch(ZARINPAL_VERIFY_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: MERCHANT_ID,
        amount: order.amountRial,
        authority: Authority
      })
    });
    const zpData = await zpResponse.json();

    // 100 = verified now, 101 = already verified before - both count as success.
    if (zpData && zpData.data && (zpData.data.code === 100 || zpData.data.code === 101)) {
      order.verified = true;
      order.refId = zpData.data.ref_id;
      db.orders[Authority] = order;
      saveDb(db);
      grantPremiumDays(deviceId, planConfig.days);
      return res.send(successHtml());
    }

    console.error('Zarinpal verify.json error:', JSON.stringify(zpData));
    return fail('تایید پرداخت توسط زرین‌پال ناموفق بود.');
  } catch (e) {
    console.error('GET /payment/callback failed:', e);
    return fail('خطای داخلی سرور در تایید پرداخت.');
  }
});

function successHtml() {
  return `
    <html dir="rtl" lang="fa"><body style="font-family:tahoma;text-align:center;padding:40px">
    <h2>✅ پرداخت با موفقیت انجام شد</h2>
    <p>اشتراک پریمیوم شما فعال شد. این صفحه را ببندید و به اپ مالیار پرو برگردید.</p>
    </body></html>`;
}

app.listen(PORT, () => {
  console.log(`Maliar Pro billing backend listening on port ${PORT} (sandbox=${SANDBOX})`);
});
