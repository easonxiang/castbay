// The changelog as an RSS feed, one item per release, for readers who follow new versions.
import rss from '@astrojs/rss';
import { BASE_URL, pagePath, type Lang } from './i18n';
import { releases } from './releases';

const channel = {
  en: { title: 'CastBay changelog', description: "What's new in each version of CastBay, the AirPlay and DLNA receiver for Android TVs, car displays and tablets.", language: 'en' },
  zh: { title: '映湾更新日志', description: '映湾投屏（安卓大屏上的 AirPlay 和 DLNA 接收器）每个版本的更新内容。', language: 'zh-CN' },
};

export async function changelogFeed(lang: Lang) {
  const { title, description, language } = channel[lang];
  const page = BASE_URL + pagePath(lang, 'changelog');
  return rss({
    title,
    description,
    site: BASE_URL,
    customData: `<language>${language}</language>`,
    items: (await releases(lang)).map((entry) => ({
      title: `${lang === 'zh' ? '映湾投屏' : 'CastBay'} ${entry.data.version}`,
      link: `${page}#v${entry.data.version}`,
      pubDate: entry.data.date,
      content: entry.rendered?.html ?? '',
    })),
  });
}
