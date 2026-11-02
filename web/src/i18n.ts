// The words the shared header and footer use, and where each page lives in each language.
export type Lang = 'en' | 'zh';
/** Every public page, each in English and Chinese; the sitemap lists them all. */
export const PAGES = ['index', 'download', 'guide', 'desktop-guide', 'tech', 'faq', 'changelog', 'compatibility', 'feedback', 'privacy'] as const;
export type PageName = (typeof PAGES)[number];

export const BASE_URL = 'https://castbay.weenas.com';

export const strings = {
  en: {
    htmlLang: 'en',
    ogLocale: 'en_US',
    prefix: '/',
    label: 'English',
    themeTitle: 'Dark or light theme',
    menu: 'Menu',
    themeToLight: 'Switch to light theme',
    themeToDark: 'Switch to dark theme',
    home: 'Home',
    guide: 'User guide',
    desktopGuide: 'Desktop guide',
    faq: 'FAQ',
    tech: 'How it works',
    changelog: 'Changelog',
    compatibility: 'Compatibility',
    feedback: 'Feedback',
    privacy: 'Privacy',
    releases: 'Releases',
    download: 'Download',
    trademarks: 'AirPlay, iPhone, iPad and Mac are trademarks of Apple Inc.; Android TV and Google TV are trademarks of Google LLC. CastBay is not affiliated with Apple or Google.',
  },
  zh: {
    htmlLang: 'zh-CN',
    ogLocale: 'zh_CN',
    prefix: '/zh/',
    label: '中文',
    themeTitle: '深色或浅色',
    menu: '菜单',
    themeToLight: '切换到浅色',
    themeToDark: '切换到深色',
    home: '主页',
    guide: '使用说明',
    desktopGuide: '桌面端说明',
    faq: '常见问题',
    tech: '技术原理',
    changelog: '更新日志',
    compatibility: '兼容性',
    feedback: '反馈',
    privacy: '隐私政策',
    releases: '版本下载',
    download: '下载',
    trademarks: 'AirPlay、iPhone、iPad、Mac 是 Apple Inc. 的商标；Android TV、Google TV 是 Google LLC 的商标。映湾（CastBay）与 Apple、Google 均无关联。',
  },
} as const;

export const otherLang = (lang: Lang): Lang => (lang === 'en' ? 'zh' : 'en');

/** A page's URL path, e.g. "/", "/guide", "/zh/", "/zh/guide". */
export function pagePath(lang: Lang, page: PageName): string {
  const prefix = strings[lang].prefix;
  return page === 'index' ? prefix : prefix + page;
}

/** The latest APK, served by the site itself (see integrations/latest-apk.mjs); short enough to type on a TV. */
export const APK_URL = 'https://castbay.weenas.com/apk';

/** The SHA-256 fingerprint of the certificate every release APK is signed with (CN=CastBay, O=weenas). */
export const CERT_SHA256 = 'a33fd34b57c7320068ccbcc2a5ee0c58e6112288048a80e609c98bc68a753d8d';

/** A date as the pages write it: "September 29, 2026" or "2026 年 9 月 29 日". */
export function formatDate(lang: Lang, date: Date): string {
  return lang === 'zh'
    ? `${date.getUTCFullYear()} 年 ${date.getUTCMonth() + 1} 月 ${date.getUTCDate()} 日`
    : date.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' });
}
