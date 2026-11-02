// The sitemap, built with the site: every page in both languages, with hreflang alternates and
// the date its source last changed in git (or a newer release it shows), so lastmod stays true.
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { BASE_URL, PAGES, pagePath, type Lang, type PageName } from '../i18n';
import { desktopReleases, releases } from '../releases';

const git = (...args: string[]) => {
  try {
    return execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return '';
  }
};

// A shallow clone dates every file to its one commit, which would be wrong: leave those out.
const fullHistory = git('rev-parse', '--is-shallow-repository') === 'false';

/** The day [page]'s source in [lang] last changed, as YYYY-MM-DD, if git knows it. */
function sourceDate(lang: Lang, page: PageName): string {
  if (!fullHistory) return '';
  const dir = lang === 'en' ? 'src/pages/' : `src/pages/${lang}/`;
  const file = ['.md', '.astro'].map((ext) => dir + page + ext).find((f) => existsSync(f));
  return file ? git('log', '-1', '--format=%cs', '--', file) : '';
}

const day = (date: Date) => date.toISOString().slice(0, 10);
const newest = (...dates: string[]) => dates.filter(Boolean).sort().at(-1) ?? '';

// Pages that show the latest version change with every release.
const showsReleases = new Set<PageName>(['index', 'download', 'changelog']);

export async function GET() {
  const [android] = await releases('en');
  const [desktop] = await desktopReleases('en');
  const released = newest(android ? day(android.data.date) : '', desktop ? day(desktop.data.date) : '');

  const urls = PAGES.flatMap((page) =>
    (['en', 'zh'] as const).map((lang) => {
      const lastmod = newest(sourceDate(lang, page), showsReleases.has(page) ? released : '');
      return [
        '  <url>',
        `    <loc>${BASE_URL}${pagePath(lang, page)}</loc>`,
        ...(lastmod ? [`    <lastmod>${lastmod}</lastmod>`] : []),
        `    <xhtml:link rel="alternate" hreflang="en" href="${BASE_URL}${pagePath('en', page)}"/>`,
        `    <xhtml:link rel="alternate" hreflang="zh-CN" href="${BASE_URL}${pagePath('zh', page)}"/>`,
        `    <xhtml:link rel="alternate" hreflang="x-default" href="${BASE_URL}${pagePath('en', page)}"/>`,
        '  </url>',
      ].join('\n');
    }),
  );

  const xml = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">',
    ...urls,
    '</urlset>',
    '',
  ].join('\n');
  return new Response(xml, { headers: { 'Content-Type': 'application/xml; charset=utf-8' } });
}
