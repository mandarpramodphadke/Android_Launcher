// Renders the Google Play store graphics from the app's own screenshots (Robolectric renders).
// Usage: node render.js <screenshotsDir> <outDir>
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const [shots, out] = process.argv.slice(2);
const file = (name) => 'file://' + path.join(shots, name + '.png');

const SPARKLE = `
  <path fill="#FFFFFF" d="M52,32C54.2,47 60,52.8 75,55C60,57.2 54.2,63 52,78C49.8,63 44,57.2 29,55C44,52.8 49.8,47 52,32Z"/>
  <path fill="rgba(255,255,255,0.9)" d="M72,28C72.9,34 75,36.1 81,37C75,37.9 72.9,40 72,46C71.1,40 69,37.9 63,37C69,36.1 71.1,34 72,28Z"/>`;
const iconSvg = (size, radius = 0) => `
  <svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 108 108">
    <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#4F46E5"/><stop offset="1" stop-color="#9333EA"/></linearGradient></defs>
    <rect width="108" height="108" rx="${radius}" fill="url(#g)"/>${SPARKLE}
  </svg>`;

const base = `
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: 'Inter', sans-serif; -webkit-font-smoothing: antialiased; overflow: hidden; }`;

const phone = (shot, width, extra = '') => `
  <div class="phone" style="width:${width}px; ${extra}">
    <img src="${file(shot)}" style="width:100%; display:block; border-radius:${Math.round(width * 0.075)}px;">
  </div>`;
const phoneCss = `
  .phone { padding: 12px; background: #0B0A14; border-radius: 64px;
           box-shadow: 0 40px 80px rgba(8, 5, 30, 0.55), 0 0 0 2px rgba(255,255,255,0.08); }`;

const screenshot = (shot, title, subtitle) => `<!doctype html><html><head><style>${base}${phoneCss}
  body { width: 1080px; height: 1920px; background: linear-gradient(160deg, #312E81 0%, #4C1D95 55%, #1E1B4B 100%); }
  .glow { position: absolute; inset: 0; background: radial-gradient(circle at 50% 30%, rgba(167,139,250,0.35), transparent 60%); }
  h1 { position: absolute; top: 120px; left: 90px; right: 90px; text-align: center; color: #fff;
       font-size: 68px; font-weight: 700; line-height: 1.12; letter-spacing: -1px; }
  p { position: absolute; top: ${title.length > 26 ? 300 : 225}px; left: 120px; right: 120px; text-align: center;
      color: rgba(237, 233, 254, 0.88); font-size: 34px; font-weight: 500; line-height: 1.3; }
  .frame { position: absolute; left: 50%; top: ${title.length > 26 ? 440 : 380}px; transform: translateX(-50%); }
  </style></head><body><div class="glow"></div><h1>${title}</h1><p>${subtitle}</p>
  <div class="frame">${phone(shot, 640)}</div></body></html>`;

const feature = `<!doctype html><html><head><style>${base}${phoneCss}
  body { width: 1024px; height: 500px; background: linear-gradient(120deg, #3730A3 0%, #6D28D9 60%, #7E22CE 100%); position: relative; }
  .glow { position: absolute; inset: 0; background: radial-gradient(circle at 78% 40%, rgba(196,181,253,0.35), transparent 55%); }
  .text { position: absolute; left: 64px; top: 120px; width: 470px; color: #fff; }
  .text h1 { font-size: 64px; font-weight: 800; letter-spacing: -1.5px; margin-top: 22px; }
  .text p { font-size: 26px; line-height: 1.35; color: rgba(237,233,254,0.92); margin-top: 12px; font-weight: 500; }
  .p1 { position: absolute; left: 590px; top: 70px; transform: rotate(-7deg); }
  .p2 { position: absolute; left: 790px; top: 40px; transform: rotate(5deg); }
  .phone { padding: 8px; border-radius: 34px; }
  </style></head><body><div class="glow"></div>
  <div class="text">${iconSvg(84, 22)}<h1>AI Launcher</h1><p>A home screen with a private AI assistant that runs on your phone.</p></div>
  <div class="p1">${phone('store-home-material-you', 210)}</div>
  <div class="p2">${phone('chat-material-you-unused-apps', 210)}</div>
  </body></html>`;

const SCREENS = [
  ['store-home-material-you', 'Your home screen, calmer', 'Pages, folders, widgets and your AI'],
  ['chat-material-you-unused-apps', 'An AI assistant that runs on your phone', 'Private by design: chats never leave your phone'],
  ['chat-classic-light-day-plan', 'Plan your day by asking', 'Agenda, to-dos and time blocks in plain words'],
  ['drawer-colourful-dark-grid', 'Every app, one swipe away', 'A–Z, AI-sorted categories and smart suggestions'],
  ['drawer-material-you-folders', 'Folders with a purpose', 'Group apps your way, plus a built-in planner'],
  ['store-home-neon', 'Make it yours', 'Six themes, from AMOLED black to Neon, and a minimal mode'],
];

(async () => {
  const browser = await chromium.launch();
  const render = async (html, width, height, target, type = 'png') => {
    const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 });
    const htmlPath = path.join(out, '.render.html');
    fs.writeFileSync(htmlPath, html);
    await page.goto('file://' + htmlPath);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(150);
    await page.screenshot({ path: target, type, ...(type === 'jpeg' ? { quality: 95 } : {}) });
    await page.close();
    fs.unlinkSync(htmlPath);
  };
  await render(`<!doctype html><html><head><style>${base} body{width:512px;height:512px}</style></head><body>${iconSvg(512)}</body></html>`, 512, 512, path.join(out, 'icon-512.png'));
  await render(feature, 1024, 500, path.join(out, 'feature-graphic.png'));
  let i = 1;
  for (const [shot, title, subtitle] of SCREENS) {
    await render(screenshot(shot, title, subtitle), 1080, 1920, path.join(out, `phone-${i++}.png`));
  }
  await browser.close();
})();
