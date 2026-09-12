// Google Apps Script version of the Maliar Pro billing backend - a free, no-hosting-
// needed alternative to the Node.js server in /server. Uses PropertiesService as a tiny
// key-value store (no Google Sheet needed) and UrlFetchApp to call the gateway's API.
//
// Supports three gateways - pick one with PAYMENT_GATEWAY ("zarinpal", "nextpay", or
// "payping"):
//   - Zarinpal needs its own merchant account approved and working.
//   - NextPay needs a "درگاه مستقیم" (direct gateway) API key, NOT the "صفحه پرداخت
//     شخصی" (personal payment page) - that one has no API for an app to call.
//   - PayPing needs an access token (Bearer) from its developer console. No manual
//     "product"/"permalink" setup needed in the PayPing dashboard - this calls /v3/pay
//     directly with the exact plan amount each time, same pattern as the other two.
//
// SETUP:
// 1. Go to https://script.google.com/ -> New project.
// 2. Delete the default code and paste this whole file in.
// 3. In "Project Settings" (gear icon) -> Script Properties, add ONE "Property" +
//    "Value" row per line below (the Property box takes the name on the left,
//    the Value box takes what's on the right):
//      PAYMENT_GATEWAY      = zarinpal   (or nextpay, or payping)
//      -- if using zarinpal --
//      ZARINPAL_MERCHANT_ID = your merchant id
//      ZARINPAL_SANDBOX     = true   (set to false once you've tested)
//      PRICE_MONTHLY_RIAL   = 1990000   (= 199,000 Toman)
//      PRICE_YEARLY_RIAL    = 18900000  (= 1,890,000 Toman)
//      -- if using nextpay --
//      NEXTPAY_API_KEY      = your direct-gateway api_key from NextPay's panel
//      PRICE_MONTHLY_TOMAN  = 199000   (NextPay's amount is in Toman, not Rial)
//      PRICE_YEARLY_TOMAN   = 1890000
//      -- if using payping --
//      PAYPING_TOKEN        = your PayPing access token (Bearer)
//      PRICE_MONTHLY_TOMAN  = 199000   (PayPing's amount is in Toman too)
//      PRICE_YEARLY_TOMAN   = 1890000
//      -- if using Bazaar / Myket in-app products too --
//      APP_PACKAGE_NAME     = com.maliar.pro
//      BAZAAR_API_TOKEN     = token from Bazaar developer dashboard
//      MYKET_ACCESS_TOKEN   = token from Myket developer dashboard
//      -- optional AI proxy --
//      AI_PROVIDER          = gapgpt (or liara)
//      GAPGPT_API_KEY       = provider key (Script Property only)
//      AI_MODEL             = gpt-4o-mini
//      AI_LIFETIME_LIMIT    = 15   (shared free AI allowance per device)
//      AI_MARKET_MODEL      = grok-4  (optional live-web fallback for product prices)
//      AI_STT_MODEL         = whisper-1  (یا gapgpt/whisper-1 برای GapGPT)
//      AI_TTS_MODEL         = gpt-4o-mini-tts  (یا tts-1)
//      -- optional second live-web fallback for بازاریار/برنامه‌غذایی/لیست‌خرید price
//         lookups (netarz.ir - OpenAI-compatible gateway, used ONLY for live price
//         search, not for the app's regular AI chat) --
//      NETARZ_API_KEY       = your netarz.ir key (starts with sk-ntz-v1-)
//      NETARZ_BASE_URL      = https://netarz.ir/api/ai/v1  (default, usually leave unset)
//      TAVILY_API_KEY       = your Tavily search key (optional, preferred for price lookup)
//      AVALAI_API_KEY       = your AvalAI key (preferred; can also serve Tavily + Sonar)
//      AVALAI_SONAR_MODEL   = sonar  (optional, default shown)
//      ENABLE_TAVILY        = false  (enable only after your AvalAI Tavily endpoint succeeds)
//      NETARZ_MARKET_MODEL  = openrouter/perplexity/sonar  (default fallback)
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

function activeGateway_() {
  const g = getSetting_('PAYMENT_GATEWAY', 'zarinpal');
  if (g === 'nextpay' || g === 'payping') return g;
  return 'zarinpal';
}

function getPlans_() {
  // Monthly: 199,000 Toman. Yearly: 1,890,000 Toman (= 1,990,000 / 18,900,000 Rial).
  if (activeGateway_() === 'nextpay' || activeGateway_() === 'payping') {
    return {
      monthly: { days: 30, amount: parseInt(getSetting_('PRICE_MONTHLY_TOMAN', '199000'), 10) },
      yearly: { days: 365, amount: parseInt(getSetting_('PRICE_YEARLY_TOMAN', '1890000'), 10) }
    };
  }
  return {
    monthly: { days: 30, amount: parseInt(getSetting_('PRICE_MONTHLY_RIAL', '1990000'), 10) },
    yearly: { days: 365, amount: parseInt(getSetting_('PRICE_YEARLY_RIAL', '18900000'), 10) }
  };
}

const PAYPING_BASE = 'https://api.payping.ir/v3';

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

// NextPay's plain-HTTP (non-SOAP) endpoints - ".http" instead of ".wsdl". Its success
// codes are its own quirky convention, not the usual "0 = ok" - a *token* request
// succeeds when code === -1, while a *verify* request succeeds when code === 0.
const NEXTPAY_TOKEN_URL = 'https://api.nextpay.org/gateway/token.http';
const NEXTPAY_VERIFY_URL = 'https://api.nextpay.org/gateway/verify.http';
const NEXTPAY_PAYMENT_BASE = 'https://api.nextpay.org/gateway/payment/';

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

function getOrder_(key) {
  const raw = PropertiesService.getScriptProperties().getProperty('order_' + key);
  return raw ? JSON.parse(raw) : null;
}

function saveOrder_(key, order) {
  PropertiesService.getScriptProperties().setProperty('order_' + key, JSON.stringify(order));
}

function getStorePurchase_(key) {
  const raw = PropertiesService.getScriptProperties().getProperty('store_purchase_' + key);
  return raw ? JSON.parse(raw) : null;
}

function saveStorePurchase_(key, purchase) {
  PropertiesService.getScriptProperties().setProperty('store_purchase_' + key, JSON.stringify(purchase));
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
  return routeRequest_(e);
}

// PayPing's payment-result callback is a real HTTP POST (application/x-www-form-urlencoded),
// unlike Zarinpal/NextPay which redirect back with a plain GET - Apps Script needs a
// separate doPost entry point to receive it. e.parameter merges the URL's own query
// params (path/deviceId/plan, which we put in the returnUrl ourselves) together with the
// POSTed form fields (paymentCode, paymentRefId, amount, etc.), so the same routing works.
function doPost(e) {
  return routeRequest_(e);
}

function routeRequest_(e) {
  const params = Object.assign({}, (e && e.parameter) || {}, parseJsonBody_(e));
  const path = params.path;

  if (path === 'status') return handleStatus_(params);
  if (path === 'request') return handleRequest_(params);
  if (path === 'callback') return handleCallback_(params);
  if (path === 'paypingCallback') return handleCallbackPayping_(params);
  if (path === 'verifyStore') return handleVerifyStore_(params);
  if (path === 'aiChat') return handleAiChat_(params);
  if (path === 'aiStt') return handleAiStt_(params);
  if (path === 'aiTts') return handleAiTts_(params);
  if (path === 'marketSearch') return handleMarketSearch_(params);
  if (path === 'marketAiSearch') return handleMarketAiSearch_(params);
  if (path === 'marketParseMessage') return handleMarketParseMessage_(params);
  if (path === 'marketDiagnostics') return handleMarketDiagnostics_(params);

  return jsonOutput_({ error: 'unknown_path' });
}

// --- Market Assistant ---------------------------------------------------------------
// The APK never calls shops/channels directly. Providers live here so credentials,
// caching, terms of use, and rate limits stay server-side.
//  - Torob/Digikala: unofficial public JSON endpoints their own web/app clients use.
//    No API key. 2026-09 diagnostics showed both now actively bot-block Google's
//    outbound IPs (Torob: an arcaptcha "are you a robot?" page; Digikala: an
//    Akamai/Cloudflare-style blank challenge redirect) rather than occasionally
//    rate-limiting - this is an external anti-bot wall, not a bug in this script, and
//    there's no reliable server-side fix for it (short of a paid scraping/proxy
//    service or an official partner API from either site). Failures are swallowed
//    per-provider so one blocked source never breaks the others.
//  - Telegram: only the *user's own* saved sources (from "منابع عمده و خرده") are
//    queried, via the public https://t.me/s/<channel> preview page (no bot token, no
//    login - this is the same page a browser sees for any public channel).
function handleMarketSearch_(params) {
  const query = String(params.query || '').trim();
  const priceType = String(params.priceType || 'retail').toLowerCase();
  if (!query || query.length > 160) return jsonOutput_({ error: 'query is required' });
  if (priceType !== 'retail' && priceType !== 'wholesale') return jsonOutput_({ error: 'invalid_price_type' });
  const sources = parseUserSources_(params.sources);
  const cacheKey = 'market_cache_' + Utilities.base64EncodeWebSafe(query + ':' + priceType + ':' + sources.map(function(s) { return s.url; }).join(',')).replace(/[+/=]/g, '').slice(0, 180);
  const cached = CacheService.getScriptCache().get(cacheKey);
  if (cached) return jsonOutput_(JSON.parse(cached));
  const results = marketProviders_(priceType, sources).reduce(function(all, provider) {
    try { return all.concat(provider(query, priceType) || []); } catch (err) {
      Logger.log('marketSearch provider(' + (provider.name || 'anonymous') + ') threw: ' + err);
      return all;
    }
  }, []);
  const response = { query: query, priceType: priceType, checkedAt: Date.now(), results: results };
  CacheService.getScriptCache().put(cacheKey, JSON.stringify(response), 900);
  return jsonOutput_(response);
}

/** Last-resort live web search. This is called only after the deterministic shop/channel
 * search returned no price and only after an explicit tap in the app.
 *
 * 2026-09: xAI retired the "live search" tool on /chat/completions (the endpoint now
 * returns HTTP 410 for it) - web_search only works through the newer Responses API
 * (POST {baseUrl}/responses, body uses "input" instead of "messages"). That is very
 * likely why this always failed with ai_unavailable: the request shape below was the
 * old, now-dead one. We try the Responses shape first and fall back to the old
 * chat-completions shape in case GapGPT's proxy needs it for a given model. Every
 * failure is Logger.log'd (visible in the Apps Script "Executions" tab for the real
 * request, no need to invoke doGet/doPost by hand) so the exact upstream error - wrong
 * model id, 404 for an unsupported endpoint, quota, etc. - is visible instead of being
 * swallowed into a single generic "ai_unavailable".
 */
function handleMarketAiSearch_(params, debugOut) {
  const query = String(params.query || '').trim();
  if (!query || query.length > 160) return jsonOutput_({ error: 'query is required' });
  // Quota-only gate: unlike handleAiChat_/handleAiStt_/handleAiTts_ this endpoint does
  // NOT require the *primary* provider (AI_PROVIDER/GAPGPT_API_KEY) to be configured,
  // because it can run entirely on the netarz.ir fallback below (used by بازاریار, and
  // by "بررسی بازار" price checks from برنامه‌غذایی/لیست‌خرید, which all share this same
  // endpoint via MarketBackendClient.search's grok-fallback call).
  const deviceId = String(params.deviceId || '').trim();
  if (!deviceId || deviceId.length > 128) return jsonOutput_({ error: 'deviceId is required' });
  if (!consumeAiQuota_(deviceId)) return jsonOutput_({ error: 'ai_daily_limit_reached', limit: aiLimit_() });

  const systemPrompt = 'Use web search for Iranian prices. Return ONLY valid JSON: {"price":number,"minPrice":number,"maxPrice":number,"source":"","sourceUrl":""}. Prices must be TOMAN. With insufficient evidence return price 0. Never invent a price or URL.';
  const userPrompt = 'Find an approximate current retail market price in Iran for: ' + query;

  // 1) Primary provider's own live-web model (e.g. grok-4 via GapGPT), when configured.
  // Falls through (instead of returning) on empty results too, not just on hard errors,
  // so a "couldn't find it" reply from Grok still lets the netarz/Perplexity fallback
  // below have a try, rather than surfacing a false "not found" to the user.
  const cfg = aiConfig_();
  if (cfg.provider === 'gapgpt' && cfg.key) {
    const model = getSetting_('AI_MARKET_MODEL', 'grok-4');
    const viaResponses = marketAiSearchViaResponses_(cfg, model, systemPrompt, userPrompt, debugOut);
    if (viaResponses && viaResponses.results && viaResponses.results.length) return jsonOutput_(viaResponses);
    const viaChat = marketAiSearchViaChatCompletions_(cfg, model, systemPrompt, userPrompt, debugOut);
    if (viaChat && viaChat.results && viaChat.results.length) return jsonOutput_(viaChat);
  } else if (debugOut) {
    debugOut.responsesApi = 'skipped (AI_PROVIDER is not gapgpt, or GAPGPT_API_KEY is unset)';
  }

  // 2) Prefer Tavily's search API: it is a direct web-search result feed and is
  // substantially cheaper/faster than asking a reasoning model to browse.
  if (getSetting_('ENABLE_TAVILY', 'false') === 'true') {
    const viaTavily = marketAiSearchViaTavily_(query, debugOut);
    if (viaTavily) { Logger.log('marketAiSearch provider=Tavily query=' + query); return jsonOutput_(viaTavily); }
  } else if (debugOut) {
    debugOut.tavily = 'disabled (ENABLE_TAVILY is false)';
  }

  // 3) Perplexity Sonar through AvalAI (same provider/model family, separate gateway).
  const viaAvalaiSonar = marketAiSearchViaAvalaiSonar_(systemPrompt, userPrompt, debugOut);
  if (viaAvalaiSonar) { Logger.log('marketAiSearch provider=Perplexity-Sonar-AvalAI query=' + query); return jsonOutput_(viaAvalaiSonar); }

  // 4) Fall back to Perplexity Sonar via netarz.ir - a separate live-search
  // model/key from the app's regular AI provider, used only here since Torob/Digikala are
  // bot-blocked and GapGPT's Grok web-search route is unreliable (410/504s - see the
  // 2026-09 diagnostics notes above marketProviders_).
  const viaNetarz = marketAiSearchViaNetarz_(systemPrompt, userPrompt, debugOut);
  if (viaNetarz) { Logger.log('marketAiSearch provider=Perplexity-Sonar query=' + query); return jsonOutput_(viaNetarz); }

  return jsonOutput_({ error: 'ai_unavailable' });
}

function marketAiSearchViaResponses_(cfg, model, systemPrompt, userPrompt, debugOut) {
  try {
    const response = aiFetch_(cfg.baseUrl + '/responses', {
      apiKey: cfg.key,
      body: {
        model: model, tools: [{ type: 'web_search' }], temperature: 0.1,
        input: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: userPrompt }
        ]
      }
    });
    const code = response.getResponseCode();
    if (code < 200 || code >= 300) {
      const detail = 'http ' + code + ': ' + response.getContentText().slice(0, 500);
      Logger.log('marketAiSearch(responses) ' + detail);
      if (debugOut) debugOut.responsesApi = detail;
      return null;
    }
    const data = JSON.parse(response.getContentText());
    if (debugOut) debugOut.responsesApi = 'http 200, raw: ' + response.getContentText().slice(0, 500);
    return parseMarketAiPrice_(extractResponsesOutputText_(data));
  } catch (err) {
    const detail = 'threw: ' + err;
    Logger.log('marketAiSearch(responses) ' + detail);
    if (debugOut) debugOut.responsesApi = detail;
    return null;
  }
}

// Falls back to the legacy chat-completions shape (kept in case a given GapGPT model
// still routes web_search there). If xAI's 410 is what the proxy forwards, this will
// also fail and the caller returns ai_unavailable, but with both attempts logged.
function marketAiSearchViaChatCompletions_(cfg, model, systemPrompt, userPrompt, debugOut) {
  try {
    const response = aiFetch_(cfg.baseUrl + '/chat/completions', {
      apiKey: cfg.key,
      body: {
        model: model, tools: [{ type: 'web_search' }], temperature: 0.1, max_tokens: 350,
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: userPrompt }
        ]
      }
    });
    const code = response.getResponseCode();
    if (code < 200 || code >= 300) {
      const detail = 'http ' + code + ': ' + response.getContentText().slice(0, 500);
      Logger.log('marketAiSearch(chat) ' + detail);
      if (debugOut) debugOut.chatCompletions = detail;
      return null;
    }
    const data = JSON.parse(response.getContentText());
    if (debugOut) debugOut.chatCompletions = 'http 200, raw: ' + response.getContentText().slice(0, 500);
    const text = String(data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content || '');
    return parseMarketAiPrice_(text);
  } catch (err) {
    const detail = 'threw: ' + err;
    Logger.log('marketAiSearch(chat) ' + detail);
    if (debugOut) debugOut.chatCompletions = detail;
    return null;
  }
}

// netarz.ir is a separate OpenAI-compatible gateway (base_url https://netarz.ir/api/ai/v1)
// used ONLY as a live-web-search fallback for product-price lookups - NOT for the app's
// regular AI chat/meal-plan text, which keeps using AI_PROVIDER (gapgpt/liara) via
// aiConfig_(). Its "openrouter/perplexity/sonar-pro-search" model does its own live web
// search on a plain /chat/completions call, no special "tools"/"web_search" param needed
// (unlike the GapGPT/Grok attempts above), per netarz's own docs.
function netarzConfig_() {
  return {
    key: getSetting_('NETARZ_API_KEY', ''),
    baseUrl: getSetting_('NETARZ_BASE_URL', 'https://netarz.ir/api/ai/v1'),
    model: getSetting_('NETARZ_MARKET_MODEL', 'openrouter/perplexity/sonar')
  };
}

/** AvalAI-hosted Tavily search fallback. AvalAI exposes Tavily at the OpenAI-compatible
 * search endpoint, so the user's AvalAI key belongs in Script Properties only. */
function marketAiSearchViaTavily_(query, debugOut) {
  const key = getSetting_('AVALAI_API_KEY', getSetting_('TAVILY_API_KEY', ''));
  if (!key) { if (debugOut) debugOut.tavily = 'skipped (TAVILY_API_KEY not configured)'; return null; }
  try {
    const response = UrlFetchApp.fetch('https://api.avalai.ir/v1/search/tavily-search', {
      method: 'post', contentType: 'application/json', muteHttpExceptions: true,
      headers: { Authorization: 'Bearer ' + key },
      payload: JSON.stringify({ query: query + ' قیمت خرده‌فروشی فعلی در ایران', max_results: 5, search_depth: 'basic', include_answer: true })
    });
    const code = response.getResponseCode();
    if (code < 200 || code >= 300) { if (debugOut) debugOut.tavily = 'http ' + code; return null; }
    const data = JSON.parse(response.getContentText());
    const text = String(data.answer || '') + '\n' + (data.results || []).map(function(r) { return String(r.title || '') + ' ' + String(r.content || r.snippet || '') + ' ' + String(r.url || ''); }).join('\n');
    const parsed = parseMarketAiPrice_(text, 'جست‌وجوی Tavily');
    if ((!parsed || !parsed.results || !parsed.results.length) && text) {
      // Tavily returns ranked snippets, not a JSON object. Extract explicit Toman
      // amounts as a conservative fallback; never guess from bare small numbers.
      const matches = text.match(/(?:قیمت|تومان|تومن|ریال)[^\d۰-۹]{0,24}([\d۰-۹][\d۰-۹,،.]*)|([\d۰-۹][\d۰-۹,،.]*)[^\d۰-۹]{0,12}(?:تومان|تومن)/gi) || [];
      const values = matches.map(function(raw) {
        const digits = String(raw).replace(/[۰-۹]/g, function(c) { return String('۰۱۲۳۴۵۶۷۸۹'.indexOf(c)); }).replace(/[^\d]/g, '');
        return Number(digits);
      }).filter(function(n) { return n >= 1000 && n < 1e12; });
      if (values.length) {
        const price = Math.round(values.reduce(function(a, b) { return a + b; }, 0) / values.length);
        return { checkedAt: Date.now(), results: [{ source: 'جست‌وجوی Tavily', sourceUrl: '', priceType: 'retail', price: price, minPrice: Math.min.apply(null, values), maxPrice: Math.max.apply(null, values), confidence: .35 }] };
      }
    }
    if (parsed && parsed.results && parsed.results.length) return parsed;
    if (debugOut) debugOut.tavily = 'no parseable price';
    return null;
  } catch (err) {
    Logger.log('marketAiSearch(tavily) threw: ' + err);
    if (debugOut) debugOut.tavily = String(err);
    return null;
  }
}

function marketAiSearchViaAvalaiSonar_(systemPrompt, userPrompt, debugOut) {
  const key = getSetting_('AVALAI_API_KEY', getSetting_('TAVILY_API_KEY', ''));
  if (!key) { if (debugOut) debugOut.avalaiSonar = 'skipped (AVALAI_API_KEY not configured)'; return null; }
  try {
    const response = aiFetch_('https://api.avalai.ir/v1/chat/completions', {
      apiKey: key,
      body: { model: getSetting_('AVALAI_SONAR_MODEL', 'sonar'), temperature: 0.1, max_tokens: 350,
        messages: [{ role: 'system', content: systemPrompt }, { role: 'user', content: userPrompt }] }
    });
    const code = response.getResponseCode();
    if (code < 200 || code >= 300) { if (debugOut) debugOut.avalaiSonar = 'http ' + code + ': ' + response.getContentText().slice(0, 300); return null; }
    const data = JSON.parse(response.getContentText());
    const text = String(data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content || '');
    return parseMarketAiPrice_(text, 'جست‌وجوی وب Perplexity (AvalAI)');
  } catch (err) {
    Logger.log('marketAiSearch(avalai-sonar) threw: ' + err);
    if (debugOut) debugOut.avalaiSonar = String(err);
    return null;
  }
}

function marketAiSearchViaNetarz_(systemPrompt, userPrompt, debugOut) {
  const cfg = netarzConfig_();
  if (!cfg.key) {
    if (debugOut) debugOut.netarz = 'skipped (NETARZ_API_KEY not configured)';
    return null;
  }
  try {
    const response = aiFetch_(cfg.baseUrl + '/chat/completions', {
      apiKey: cfg.key,
      body: {
        model: cfg.model, temperature: 0.1, max_tokens: 350,
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: userPrompt }
        ]
      }
    });
    const code = response.getResponseCode();
    if (code < 200 || code >= 300) {
      const detail = 'http ' + code + ': ' + response.getContentText().slice(0, 500);
      Logger.log('marketAiSearch(netarz) ' + detail);
      if (debugOut) debugOut.netarz = detail;
      return null;
    }
    const data = JSON.parse(response.getContentText());
    if (debugOut) debugOut.netarz = 'http 200, raw: ' + response.getContentText().slice(0, 500);
    const text = String(data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content || '');
    return parseMarketAiPrice_(text, 'جست‌وجوی وب Perplexity');
  } catch (err) {
    const detail = 'threw: ' + err;
    Logger.log('marketAiSearch(netarz) ' + detail);
    if (debugOut) debugOut.netarz = detail;
    return null;
  }
}

// Responses API replies with {output: [{content: [{type:'output_text', text: '...'}]}]}
// (some proxies also add a convenience top-level output_text string) - handle both.
function extractResponsesOutputText_(data) {
  if (data && data.output_text) return String(data.output_text);
  const items = (data && data.output) || [];
  for (var i = 0; i < items.length; i++) {
    const content = items[i].content || [];
    for (var j = 0; j < content.length; j++) {
      const part = content[j];
      if (part && (part.type === 'output_text' || part.type === 'text') && part.text) return String(part.text);
    }
  }
  return '';
}

function parseMarketAiPrice_(text, defaultSource) {
  const match = String(text || '').match(/\{[\s\S]*\}/);
  if (!match) return { results: [], checkedAt: Date.now() };
  try {
    const result = JSON.parse(match[0]);
    const price = Number(result.price || 0);
    if (!(price > 0) || !isFinite(price)) return { results: [], checkedAt: Date.now() };
    return { checkedAt: Date.now(), results: [{ source: String(result.source || defaultSource || 'جست‌وجوی وب Grok').slice(0, 120), sourceUrl: String(result.sourceUrl || '').slice(0, 500), priceType: 'retail', price: price, minPrice: Number(result.minPrice || price) || price, maxPrice: Number(result.maxPrice || price) || price, confidence: 0.45 }] };
  } catch (err) {
    return { results: [], checkedAt: Date.now() };
  }
}

// Browser-testable version of testMarketDiagnostics_ - open in any browser:
//   {AI_BACKEND_URL}?path=marketDiagnostics&secret=YOUR_DEBUG_SECRET
// (set a DEBUG_SECRET Script Property to any string you pick first, so this can't be
// hit by anyone else even though the web app itself is deployed as "Anyone"). Returns
// the same JSON directly on the page - no Apps Script editor / log panel needed.
function handleMarketDiagnostics_(params) {
  const secret = getSetting_('DEBUG_SECRET', '');
  if (!secret || String(params.secret || '') !== secret) return jsonOutput_({ error: 'forbidden' });
  const query = String(params.query || 'هندزفری').trim() || 'هندزفری';
  const out = {};
  const debug = {};
  try { out.torob = torobSearch_(query, debug); } catch (err) { out.torob = 'threw: ' + err; }
  try { out.digikala = digikalaSearch_(query, debug); } catch (err) { out.digikala = 'threw: ' + err; }
  // Unique deviceId per run - a debug endpoint shouldn't eat into the real per-device
  // daily AI quota (which is what caused 'ai_daily_limit_reached' on repeated tests).
  const debugDeviceId = 'diagnostic-' + Utilities.getUuid();
  try { out.aiSearch = JSON.parse(handleMarketAiSearch_({ query: query, deviceId: debugDeviceId }, debug).getContent()); }
  catch (err) { out.aiSearch = 'threw: ' + err; }
  // Isolates whether grok-4 itself is reachable/fast via GapGPT, or whether it's
  // specifically the web_search tool that causes the 504s seen above (a plain, no-tool
  // chat call is much cheaper for their gateway than one that has to actually browse).
  try { debug.plainGrokChatNoTool = marketAiPlainProbe_(); } catch (err) { debug.plainGrokChatNoTool = 'threw: ' + err; }
  // Comparison point: does a plain call to the SAME account's already-working general
  // model (cfg.model, e.g. gpt-4o-mini) succeed while Grok-related calls time out? If
  // so, this is a Grok-specific/plan-permission issue on the GapGPT side, not a general
  // GapGPT/network outage.
  try { debug.plainGpt4oMiniChat = marketAiPlainProbeModel_(aiConfig_().model); } catch (err) { debug.plainGpt4oMiniChat = 'threw: ' + err; }
  // Ground truth: exactly which model ids this GapGPT account can actually use, straight
  // from their account (avoids guessing at names like "grok-4" vs "grok-3-mini" etc.).
  try { debug.availableModels = marketAiListModels_(); } catch (err) { debug.availableModels = 'threw: ' + err; }
  out.debug = debug;
  out.aiConfig = (function() { const c = aiConfig_(); return { provider: c.provider, baseUrl: c.baseUrl, hasKey: !!c.key, model: c.model }; })();
  out.marketModel = getSetting_('AI_MARKET_MODEL', 'grok-4');
  out.netarzConfig = (function() { const c = netarzConfig_(); return { baseUrl: c.baseUrl, hasKey: !!c.key, model: c.model }; })();
  return jsonOutput_(out);
}

function marketAiPlainProbe_() {
  return marketAiPlainProbeModel_(getSetting_('AI_MARKET_MODEL', 'grok-4'));
}

function marketAiPlainProbeModel_(model) {
  const cfg = aiConfig_();
  const response = aiFetch_(cfg.baseUrl + '/chat/completions', {
    apiKey: cfg.key,
    body: { model: model, max_tokens: 10, messages: [{ role: 'user', content: 'Reply with the single word: ok' }] }
  });
  const code = response.getResponseCode();
  return 'model=' + model + ' http ' + code + ': ' + response.getContentText().slice(0, 300);
}

// Standard OpenAI-compatible GET {baseUrl}/models - lists exactly which model ids this
// GapGPT account can actually call, so AI_MARKET_MODEL can be set to a real, current
// value instead of guessing (GapGPT's own naming for Grok has been observed to be
// e.g. "grok-3-mini" rather than "grok-4", and it changes as xAI retires model ids).
function marketAiListModels_() {
  const cfg = aiConfig_();
  const response = UrlFetchApp.fetch(cfg.baseUrl + '/models', {
    method: 'get', muteHttpExceptions: true, headers: { Authorization: 'Bearer ' + cfg.key }
  });
  const code = response.getResponseCode();
  if (code < 200 || code >= 300) return 'http ' + code + ': ' + response.getContentText().slice(0, 500);
  try {
    const data = JSON.parse(response.getContentText());
    const ids = ((data && data.data) || []).map(function(m) { return m.id; });
    const grokIds = ids.filter(function(id) { return /grok/i.test(id); });
    return { grokModelIds: grokIds, totalModelCount: ids.length };
  } catch (err) {
    return 'http 200 but unparsable: ' + response.getContentText().slice(0, 500);
  }
}

// Manual diagnostic for بازاریار: select "testMarketDiagnostics_" in the function
// dropdown at the top of the Apps Script editor and click Run. The small "Execution
// started/completed" popup does NOT show Logger.log output, so instead of relying on
// any log panel, the full result is written to a Script Property - open Project
// Settings (gear icon) -> Script Properties and look for "LAST_MARKET_DIAGNOSTIC".
// Prefer handleMarketDiagnostics_ above (open the URL in a browser) if this doesn't
// show up either - it sidesteps the editor entirely.
function testMarketDiagnostics_() {
  const query = 'هندزفری';
  const out = {};
  try { out.torob = torobSearch_(query); } catch (err) { out.torob = 'threw: ' + err; }
  try { out.digikala = digikalaSearch_(query); } catch (err) { out.digikala = 'threw: ' + err; }
  try { out.aiSearch = JSON.parse(handleMarketAiSearch_({ query: query, deviceId: 'diagnostic-test-device' }).getContent()); }
  catch (err) { out.aiSearch = 'threw: ' + err; }
  out.aiConfig = (function() { const c = aiConfig_(); return { provider: c.provider, baseUrl: c.baseUrl, hasKey: !!c.key, model: c.model }; })();
  out.marketModel = getSetting_('AI_MARKET_MODEL', 'grok-4');
  out.netarzConfig = (function() { const c = netarzConfig_(); return { baseUrl: c.baseUrl, hasKey: !!c.key, model: c.model }; })();
  const text = JSON.stringify(out, null, 2);
  PropertiesService.getScriptProperties().setProperty('LAST_MARKET_DIAGNOSTIC', text);
  Logger.log(text); // kept too, in case the log panel does pick it up
  return text;
}

// Only accepts {name, url} pairs the user already saved on-device via "افزودن منبع"
// (MarketSource). Capped and length-limited before any network call is made from them.
function parseUserSources_(raw) {
  if (!raw) return [];
  let list = raw;
  if (typeof raw === 'string') { try { list = JSON.parse(raw); } catch (err) { return []; } }
  if (!Array.isArray(list)) return [];
  return list.slice(0, 8).map(function(s) {
    return { name: String((s && s.name) || '').trim().slice(0, 80), url: String((s && s.url) || '').trim().slice(0, 300) };
  }).filter(function(s) { return s.url; });
}

function marketProviders_(priceType, sources) {
  // Each provider is a (query, priceType) -> [{name, price, minPrice, maxPrice, url,
  // source, confidence}] function. Torob/Digikala only make sense for retail; wholesale
  // pricing in Iran mostly lives in Telegram supplier channels, which is why those are
  // driven entirely by the user's own saved sources for both price types.
  // Temporarily disabled at the user's request: Torob, Digikala and Telegram are
  // currently slow/blocked. Price lookup goes directly to Tavily, then Sonar.
  Logger.log('marketProviders_: deterministic providers disabled; using Tavily -> Sonar');
  return [];
}

function telegramChannelUsername_(url) {
  const m = String(url || '').match(/t(?:elegram)?\.me\/(?:s\/)?@?([A-Za-z0-9_]{4,})/i);
  return m ? m[1] : null;
}

function torobSearch_(query, debugOut) {
  const url = 'https://api.torob.com/v4/base-product/search/?page=0&sort=popularity&size=8&source=next_desktop&query=' + encodeURIComponent(query) + '&q=' + encodeURIComponent(query);
  const res = UrlFetchApp.fetch(url, {
    muteHttpExceptions: true, followRedirects: true,
    headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Accept': 'application/json', 'Referer': 'https://torob.com/search/?query=' + encodeURIComponent(query) }
  });
  const code = res.getResponseCode();
  if (code !== 200) {
    const detail = 'http ' + code + ': ' + res.getContentText().slice(0, 500);
    Logger.log('torobSearch_ ' + detail);
    if (debugOut) debugOut.torob = detail;
    return [];
  }
  const data = JSON.parse(res.getContentText());
  const list = (data && data.results) || [];
  if (debugOut) debugOut.torob = 'http 200, ' + list.length + ' raw results, body: ' + res.getContentText().slice(0, 500);
  if (!list.length) Logger.log('torobSearch_ 200 but no results for "' + query + '": ' + res.getContentText().slice(0, 300));
  return list.slice(0, 6).map(function(item) {
    const price = Number(item.price1 || item.price || 0);
    if (!(price > 0)) return null;
    return {
      source: 'ترب' + (item.shop_text ? ' · ' + item.shop_text : ''), priceType: 'retail', price: price,
      minPrice: price, maxPrice: Number(item.price2 || price) || price, confidence: 0.7,
      name: item.name1 || item.name || '', url: item.web_client_absolute_url ? ('https://torob.com' + item.web_client_absolute_url) : ''
    };
  }).filter(function(r) { return r; });
}

function digikalaSearch_(query, debugOut) {
  const url = 'https://api.digikala.com/v1/search/?q=' + encodeURIComponent(query) + '&page=1';
  const res = UrlFetchApp.fetch(url, {
    muteHttpExceptions: true, followRedirects: true,
    headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Accept': 'application/json', 'Referer': 'https://www.digikala.com/search/?q=' + encodeURIComponent(query) }
  });
  const code = res.getResponseCode();
  if (code !== 200) {
    const detail = 'http ' + code + ': ' + res.getContentText().slice(0, 500);
    Logger.log('digikalaSearch_ ' + detail);
    if (debugOut) debugOut.digikala = detail;
    return [];
  }
  const data = JSON.parse(res.getContentText());
  const list = (data && data.data && (data.data.products || data.data.sellable_products)) || [];
  if (debugOut) debugOut.digikala = 'http 200, ' + list.length + ' raw results, body: ' + res.getContentText().slice(0, 500);
  if (!list.length) Logger.log('digikalaSearch_ 200 but no results for "' + query + '": ' + res.getContentText().slice(0, 300));
  return list.slice(0, 6).map(function(item) {
    const variant = item.default_variant || (item.variants && item.variants[0]) || {};
    const rial = Number((variant.price && (variant.price.selling_price || variant.price.rrp_price)) || item.price || 0);
    const price = Math.round(rial / 10); // Digikala prices are in Rial; app uses Toman.
    if (!(price > 0)) return null;
    return {
      source: 'دیجی‌کالا', priceType: 'retail', price: price, confidence: 0.7,
      name: item.title_fa || item.title || '', url: item.url && item.url.uri ? ('https://www.digikala.com' + item.url.uri) : ''
    };
  }).filter(function(r) { return r; });
}

// Reads the public "instant view" preview of a Telegram channel (what a browser sees
// with no login) and reuses the same price-extraction pass as pasted-message parsing.
// Only channels the user explicitly saved as a source are ever fetched.
function telegramChannelSearch_(channel, label, query) {
  const res = UrlFetchApp.fetch('https://t.me/s/' + encodeURIComponent(channel), {
    muteHttpExceptions: true, followRedirects: true, headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }
  });
  if (res.getResponseCode() !== 200) return [];
  const html = res.getContentText();
  const terms = query.trim().toLowerCase().split(/\s+/).filter(function(t) { return t.length > 1; });
  const blocks = html.split('tgme_widget_message ').slice(1).slice(-25); // most recent ~25 posts
  const results = [];
  blocks.forEach(function(block) {
    const text = block.replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, ' ')
      .replace(/&amp;/g, '&').replace(/&nbsp;/g, ' ').replace(/&#39;/g, "'").replace(/&quot;/g, '"').trim();
    const lower = text.toLowerCase();
    if (terms.length && !terms.some(function(t) { return lower.indexOf(t) !== -1; })) return;
    const parsed = extractPriceInfo_(text);
    if (!parsed.prices.length) return;
    results.push({
      source: label, priceType: parsed.priceType, price: parsed.prices[0], minPrice: parsed.minPrice,
      maxPrice: parsed.maxPrice, confidence: 0.5, name: parsed.nameHint, url: 'https://t.me/' + channel
    });
  });
  return results.slice(0, 5);
}

// Shared plain-text -> price extraction used by both pasted-message parsing
// (handleMarketParseMessage_) and the Telegram channel adapter above.
function extractPriceInfo_(text) {
  const faDigits = '۰۱۲۳۴۵۶۷۸۹';
  const prices = [];
  const re = /(^|[^0-9۰-۹])([0-9۰-۹][0-9۰-۹,٫]*)(?:\s*(?:تومان|ت))?/g;
  let match;
  while ((match = re.exec(text)) !== null) {
    const value = Number(String(match[2]).replace(/[۰-۹]/g, function(d) { return String(faDigits.indexOf(d)); }).replace(/[٫,]/g, ''));
    if (value >= 1000) prices.push(value);
  }
  return {
    nameHint: text.split('\n')[0].trim().slice(0, 120),
    priceType: /عمده|تعداد|کارتن|همکار/.test(text) ? 'wholesale' : 'retail',
    prices: prices, minPrice: prices.length ? Math.min.apply(null, prices) : null,
    maxPrice: prices.length ? Math.max.apply(null, prices) : null, confidence: prices.length ? 0.45 : 0
  };
}

function handleMarketParseMessage_(params) {
  const text = String(params.text || '').trim();
  if (!text || text.length > 8000) return jsonOutput_({ error: 'text is required' });
  return jsonOutput_(extractPriceInfo_(text));
}

function parseJsonBody_(e) {
  try {
    const raw = e && e.postData && e.postData.contents;
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (err) {
    return {};
  }
}

function aiConfig_() {
  const provider = getSetting_('AI_PROVIDER', 'gapgpt').toLowerCase();
  const isLiara = provider === 'liara';
  return {
    provider: provider,
    key: getSetting_(isLiara ? 'LIARA_API_KEY' : 'GAPGPT_API_KEY', ''),
    baseUrl: getSetting_(
      isLiara ? 'LIARA_BASE_URL' : 'GAPGPT_BASE_URL',
      isLiara ? 'https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1' : 'https://api.gapgpt.app/v1'
    ),
    model: getSetting_('AI_MODEL', isLiara ? 'openai/gpt-4o-mini' : 'gpt-4o-mini')
  };
}

function aiLimit_() {
  // One shared free allowance per device. This deliberately ignores the legacy
  // AI_DAILY_LIMIT property so an old deployment cannot silently reintroduce a second
  // 10-per-day quota while the app displays the unified 15-message allowance.
  return Math.max(1, parseInt(getSetting_('AI_LIFETIME_LIMIT', '15'), 10));
}

function aiUsageKey_(deviceId) {
  const digest = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(deviceId));
  const safe = Utilities.base64EncodeWebSafe(digest).replace(/=+$/, '');
  return 'ai_usage_lifetime_' + safe;
}

function consumeAiQuota_(deviceId) {
  if (!deviceId || String(deviceId).length > 128) return false;
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const props = PropertiesService.getScriptProperties();
    const key = aiUsageKey_(deviceId);
    const used = parseInt(props.getProperty(key) || '0', 10);
    if (used >= aiLimit_()) return false;
    props.setProperty(key, String(used + 1));
    return true;
  } finally {
    lock.releaseLock();
  }
}

function requireAiFields_(params) {
  const deviceId = String(params.deviceId || '').trim();
  if (!deviceId || deviceId.length > 128) return jsonOutput_({ error: 'deviceId is required' });
  if (!consumeAiQuota_(deviceId)) return jsonOutput_({ error: 'ai_daily_limit_reached', limit: aiLimit_() });
  const cfg = aiConfig_();
  if (!cfg.key) return jsonOutput_({ error: 'ai_provider_not_configured' });
  return null;
}

function aiFetch_(url, payload) {
  const response = UrlFetchApp.fetch(url, {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + payload.apiKey },
    payload: JSON.stringify(payload.body)
  });
  return response;
}

function handleAiChat_(params) {
  const denied = requireAiFields_(params);
  if (denied) return denied;
  const messages = params.messages;
  if (!Array.isArray(messages) || messages.length === 0 || messages.length > 40) {
    return jsonOutput_({ error: 'messages are required' });
  }
  const cfg = aiConfig_();
  try {
    const response = aiFetch_(cfg.baseUrl + '/chat/completions', {
      apiKey: cfg.key,
      body: {
        model: cfg.model,
        messages: messages,
        max_tokens: Number(params.maxTokens || 500),
        temperature: Number(params.temperature || 0.7)
      }
    });
    if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
      return jsonOutput_({ error: 'ai_unavailable' });
    }
    const data = JSON.parse(response.getContentText());
    return jsonOutput_({ text: data.choices && data.choices[0] && data.choices[0].message
      ? String(data.choices[0].message.content || '').trim() : '' });
  } catch (err) {
    return jsonOutput_({ error: 'ai_unavailable' });
  }
}

function handleAiStt_(params) {
  const denied = requireAiFields_(params);
  if (denied) return denied;
  const encoded = String(params.audioBase64 || '');
  if (!encoded || encoded.length > 10000000) return jsonOutput_({ error: 'audioBase64 is required' });
  const cfg = aiConfig_();
  try {
    const response = UrlFetchApp.fetch(cfg.baseUrl + '/audio/transcriptions', {
      method: 'post',
      muteHttpExceptions: true,
      headers: { Authorization: 'Bearer ' + cfg.key },
      payload: {
        model: getSetting_('AI_STT_MODEL', 'whisper-1'),
        file: Utilities.newBlob(Utilities.base64Decode(encoded), 'audio/mp4', 'audio.m4a')
      }
    });
    if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
      return jsonOutput_({ error: 'stt_unavailable' });
    }
    return jsonOutput_({ text: String(JSON.parse(response.getContentText()).text || '') });
  } catch (err) {
    return jsonOutput_({ error: 'stt_unavailable' });
  }
}

function handleAiTts_(params) {
  const denied = requireAiFields_(params);
  if (denied) return denied;
  const text = String(params.text || '').trim();
  if (!text || text.length > 4000) return jsonOutput_({ error: 'text is required' });
  const cfg = aiConfig_();
  try {
    let response = aiFetch_(cfg.baseUrl + '/audio/speech', {
      apiKey: cfg.key,
      body: { model: getSetting_('AI_TTS_MODEL', 'gpt-4o-mini-tts'), voice: 'alloy', input: text }
    });
    if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
      response = aiFetch_(cfg.baseUrl + '/audio/speech', {
        apiKey: cfg.key,
        body: { model: getSetting_('AI_TTS_MODEL_FALLBACK', 'tts-1'), voice: 'alloy', input: text }
      });
    }
    if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
      return jsonOutput_({ error: 'tts_unavailable' });
    }
    return jsonOutput_({
      audioBase64: Utilities.base64Encode(response.getBlob().getBytes()),
      mimeType: 'audio/mpeg'
    });
  } catch (err) {
    return jsonOutput_({ error: 'tts_unavailable' });
  }
}

// --- Bazaar / Myket in-app purchase verification (optional) -------------------------
// Only needed if you want people who installed from Bazaar/Myket to pay through the
// store's own in-app purchase sheet instead of a browser. Add these Script Properties:
//   APP_PACKAGE_NAME, BAZAAR_API_TOKEN, MYKET_ACCESS_TOKEN
// Confirmed against each store's own docs (July 2026): BOTH now use a single static
// per-app token in a request header - no OAuth2, no refresh, nothing to renew. SKUs must
// match BazaarBillingHelper.SKU_*/MyketBillingHelper.SKU_* on the Android side, and must
// be created as CONSUMABLE in-app products (Myket has no real subscription product type).

const PLAN_TO_SKU_ = { monthly: 'maliar_pro_monthly', yearly: 'maliar_pro_yearly' };

/** Confirmed against Bazaar's official "راه اندازی API (روش جدید)" و "ارسال درخواست
 *  به API بازار (روش جدید)" docs:
 *  GET https://pardakht.cafebazaar.ir/devapi/v2/api/validate/{PACKAGE_NAME}/inapp/{SKU}/purchases/{PURCHASE_TOKEN}
 *  Header: CAFEBAZAAR-PISHKHAN-API-SECRET: {TOKEN}
 *  Response.purchaseState: 0 = purchased normally, 1 = refunded. */
function verifyBazaarPurchase_(sku, purchaseToken) {
  const apiSecret = getSetting_('BAZAAR_API_TOKEN', '');
  if (!apiSecret) return false;
  const packageName = getSetting_('APP_PACKAGE_NAME', 'com.maliar.pro');
  const url = 'https://pardakht.cafebazaar.ir/devapi/v2/api/validate/' + packageName +
    '/inapp/' + sku + '/purchases/' + purchaseToken;
  const response = UrlFetchApp.fetch(url, {
    method: 'get',
    muteHttpExceptions: true,
    headers: { 'CAFEBAZAAR-PISHKHAN-API-SECRET': apiSecret }
  });
  if (response.getResponseCode() !== 200) return false;
  const data = JSON.parse(response.getContentText());
  return data.purchaseState === 0 || data.purchaseState === '0';
}

/** Confirmed against Myket's official "استفاده از API صحت سنجی خرید" docs:
 *  POST https://developer.myket.ir/api/partners/applications/{PACKAGE_NAME}/purchases/products/{SKU_ID}/verify
 *  Header: X-Access-Token: {ACCESS_TOKEN}   Body: { "tokenId": "{TOKEN_ID}" }
 *  Response.purchaseState: 0 = successful purchase, 1 = failed. */
function verifyMyketPurchase_(sku, purchaseToken) {
  const accessToken = getSetting_('MYKET_ACCESS_TOKEN', '');
  if (!accessToken) return false;
  const packageName = getSetting_('APP_PACKAGE_NAME', 'com.maliar.pro');
  const url = 'https://developer.myket.ir/api/partners/applications/' + packageName +
    '/purchases/products/' + sku + '/verify';
  const response = UrlFetchApp.fetch(url, {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    headers: { 'X-Access-Token': accessToken },
    payload: JSON.stringify({ tokenId: purchaseToken })
  });
  if (response.getResponseCode() !== 200) return false;
  const data = JSON.parse(response.getContentText());
  return data.purchaseState === 0 || data.purchaseState === '0';
}

function handleVerifyStore_(params) {
  const deviceId = params.deviceId;
  const plan = params.plan;
  const channel = params.channel;
  const purchaseToken = params.purchaseToken;
  const planConfig = getPlans_()[plan];
  const sku = PLAN_TO_SKU_[plan];

  if (!deviceId || !planConfig || !sku || !purchaseToken || (channel !== 'bazaar' && channel !== 'myket')) {
    return jsonOutput_({ verified: false, error: 'invalid_request' });
  }

  try {
    const purchaseKey = channel + ':' + purchaseToken;
    const existing = getStorePurchase_(purchaseKey);
    if (existing) {
      if (existing.deviceId !== deviceId || existing.plan !== plan) {
        return jsonOutput_({ verified: false, error: 'purchase_already_linked' });
      }
      return jsonOutput_({ verified: true, premiumUntil: getDeviceRecord_(deviceId).premiumUntil || 0, alreadyProcessed: true });
    }
    const verified = (channel === 'bazaar')
      ? verifyBazaarPurchase_(sku, purchaseToken)
      : verifyMyketPurchase_(sku, purchaseToken);

    if (!verified) {
      return jsonOutput_({ verified: false, premiumUntil: getDeviceRecord_(deviceId).premiumUntil || 0 });
    }

    // Serialize the check-and-grant step so two simultaneous callbacks cannot add the
    // same receipt twice.
    const lock = LockService.getScriptLock();
    if (!lock.tryLock(10000)) return jsonOutput_({ verified: false, error: 'verification_busy' });
    try {
      const completed = getStorePurchase_(purchaseKey);
      if (completed) {
        return jsonOutput_({ verified: true, premiumUntil: getDeviceRecord_(deviceId).premiumUntil || 0, alreadyProcessed: true });
      }
      saveStorePurchase_(purchaseKey, { deviceId: deviceId, plan: plan, channel: channel, createdAt: Date.now() });
      const premiumUntil = grantPremiumDays_(deviceId, planConfig.days);
      return jsonOutput_({ verified: true, premiumUntil: premiumUntil });
    } finally {
      lock.releaseLock();
    }
  } catch (err) {
    return jsonOutput_({ verified: false, error: 'verification_failed' });
  }
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

  const gateway = activeGateway_();
  if (gateway === 'nextpay') return handleRequestNextpay_(deviceId, plan, planConfig);
  if (gateway === 'payping') return handleRequestPayping_(deviceId, plan, planConfig);
  return handleRequestZarinpal_(deviceId, plan, planConfig);
}

function handleRequestPayping_(deviceId, plan, planConfig) {
  const token = getSetting_('PAYPING_TOKEN', '');
  if (!token) return jsonOutput_({ error: 'PAYPING_TOKEN is not configured' });

  const selfUrl = ScriptApp.getService().getUrl();
  const clientRefId = deviceId + '_' + plan + '_' + Date.now();
  const returnUrl = selfUrl + '?path=paypingCallback&deviceId=' + encodeURIComponent(deviceId) + '&plan=' + plan;

  const response = UrlFetchApp.fetch(PAYPING_BASE + '/pay', {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + token },
    payload: JSON.stringify({
      amount: planConfig.amount,
      returnUrl: returnUrl,
      description: 'مالیار پرو - ' + (plan === 'monthly' ? 'اشتراک ماهانه' : 'اشتراک سالانه'),
      clientRefId: clientRefId
    })
  });

  const data = JSON.parse(response.getContentText());
  if (data && data.paymentCode) {
    const order = { deviceId: deviceId, plan: plan, amount: planConfig.amount, paymentCode: data.paymentCode, clientRefId: clientRefId, verified: false };
    saveOrder_(data.paymentCode, order);
    saveOrder_(clientRefId, order);
    return jsonOutput_({ paymentUrl: data.url || (PAYPING_BASE + '/pay/start/' + data.paymentCode) });
  }

  return jsonOutput_({ error: 'payping_request_failed', details: data });
}

function handleRequestZarinpal_(deviceId, plan, planConfig) {
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
      amount: planConfig.amount,
      callback_url: callbackUrl,
      description: 'مالیار پرو - ' + (plan === 'monthly' ? 'اشتراک ماهانه' : 'اشتراک سالانه')
    })
  });

  const data = JSON.parse(response.getContentText());
  if (data && data.data && data.data.code === 100) {
    const authority = data.data.authority;
    saveOrder_(authority, { deviceId: deviceId, plan: plan, amount: planConfig.amount, verified: false });
    return jsonOutput_({ paymentUrl: urls.startPay + authority });
  }

  return jsonOutput_({ error: 'zarinpal_request_failed', details: data });
}

function handleRequestNextpay_(deviceId, plan, planConfig) {
  const apiKey = getSetting_('NEXTPAY_API_KEY', '');
  if (!apiKey) return jsonOutput_({ error: 'NEXTPAY_API_KEY is not configured' });

  const selfUrl = ScriptApp.getService().getUrl();
  // order_id just needs to be unique per attempt - NextPay hands it back on callback,
  // it's not looked up here (the trans_id it also hands back is the real order key).
  const orderId = deviceId + '_' + plan + '_' + Date.now();
  const callbackUrl = selfUrl + '?path=callback&deviceId=' + encodeURIComponent(deviceId) + '&plan=' + plan;

  const response = UrlFetchApp.fetch(NEXTPAY_TOKEN_URL, {
    method: 'post',
    contentType: 'application/x-www-form-urlencoded',
    muteHttpExceptions: true,
    payload: {
      api_key: apiKey,
      order_id: orderId,
      amount: String(planConfig.amount),
      callback_uri: callbackUrl
    }
  });

  const data = JSON.parse(response.getContentText());
  // NextPay's own convention: -1 means the token was created successfully here (0 is
  // reserved for a *verified payment*, not this step - easy to trip over).
  if (data && Number(data.code) === -1 && data.trans_id) {
    saveOrder_(data.trans_id, { deviceId: deviceId, plan: plan, amount: planConfig.amount, orderId: orderId, verified: false });
    return jsonOutput_({ paymentUrl: NEXTPAY_PAYMENT_BASE + data.trans_id });
  }

  return jsonOutput_({ error: 'nextpay_request_failed', details: data });
}

function handleCallback_(params) {
  return (activeGateway_() === 'nextpay')
    ? handleCallbackNextpay_(params)
    : handleCallbackZarinpal_(params);
}

function handleCallbackPayping_(params) {
  // PayPing posts the result back to returnUrl. Field names have changed between
  // documentation versions, so accept the common spellings and verify against the
  // original order saved under paymentCode before granting premium access.
  const paymentCode = params.paymentCode || params.PaymentCode || params.code;
  const clientRefId = params.clientRefId || params.ClientRefId || params.client_ref_id;
  const refId = params.paymentRefId || params.refId || params.RefId || params.refid || '';

  if (!paymentCode && !clientRefId) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>پرداخت لغو شد یا اطلاعات برگشتی ناقص بود.</p>');
  }

  const order = getOrder_(paymentCode) || getOrder_(clientRefId);
  if (!order) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>این تراکنش شناخته نشده است.</p>');
  }
  if (order.verified) {
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال است.</p>');
  }
  if (params.amount && Number(params.amount) !== Number(order.amount)) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>مبلغ برگشتی با سفارش ثبت‌شده همخوانی ندارد.</p>');
  }

  const token = getSetting_('PAYPING_TOKEN', '');
  if (!token) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>توکن پی‌پینگ روی سرور تنظیم نشده است.</p>');
  }

  const response = UrlFetchApp.fetch(PAYPING_BASE + '/pay/verify', {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + token },
    payload: JSON.stringify({
      paymentCode: paymentCode || order.paymentCode,
      clientRefId: clientRefId || order.clientRefId,
      paymentRefId: refId,
      amount: order.amount
    })
  });

  const status = response.getResponseCode();
  const text = response.getContentText();
  const data = text ? JSON.parse(text) : {};

  if (status >= 200 && status < 300) {
    order.verified = true;
    order.refId = refId;
    order.verifyResponse = data;
    saveOrder_(paymentCode || order.paymentCode, order);
    saveOrder_(clientRefId || order.clientRefId, order);
    grantPremiumDays_(order.deviceId, getPlans_()[order.plan].days);
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال شد.</p>');
  }

  return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>تایید پرداخت توسط پی‌پینگ ناموفق بود.</p>');
}

function handleCallbackZarinpal_(params) {
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
    payload: JSON.stringify({ merchant_id: merchantId, amount: order.amount, authority: authority })
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

function handleCallbackNextpay_(params) {
  // NextPay redirects back with these after the payer finishes (or cancels) on its page.
  const transId = params.trans_id;
  const deviceId = params.deviceId;
  const plan = params.plan;
  const planConfig = getPlans_()[plan];

  if (!transId || !deviceId || !planConfig) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>پرداخت لغو شد یا اطلاعات ناقص بود.</p>');
  }

  const order = getOrder_(transId);
  if (!order) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>این تراکنش شناخته نشده است.</p>');
  }
  if (order.verified) {
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال است.</p>');
  }

  const apiKey = getSetting_('NEXTPAY_API_KEY', '');
  const response = UrlFetchApp.fetch(NEXTPAY_VERIFY_URL, {
    method: 'post',
    contentType: 'application/x-www-form-urlencoded',
    muteHttpExceptions: true,
    payload: {
      api_key: apiKey,
      order_id: order.orderId,
      amount: String(order.amount),
      trans_id: transId
    }
  });
  const data = JSON.parse(response.getContentText());

  // Unlike the token step above, a *verify* success is code === 0 here - this is
  // NextPay's own convention, not a typo copied from the token step.
  if (data && Number(data.code) === 0) {
    order.verified = true;
    saveOrder_(transId, order);
    grantPremiumDays_(deviceId, planConfig.days);
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال شد.</p>');
  }

  return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>تایید پرداخت توسط نکست‌پی ناموفق بود.</p>');
}

