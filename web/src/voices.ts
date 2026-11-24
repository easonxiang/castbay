// Real user words about CastBay. Each entry links to the original post or comment,
// so anyone can check it. To add a voice, append an entry here — both the English
// and the Chinese page render from this one list, and the quote stays in the
// language it was written in. Do not paraphrase a quote: if the original text
// cannot be read word for word, leave the entry out until it can be.
export type Voice = {
  name: string;
  handle?: string;
  platform: 'GitHub' | 'Threads' | 'X' | 'ZNDS';
  /** ISO date, e.g. '2026-11-24'. */
  date: string;
  /** The TV, box or phone the person used, when they said so. */
  device?: string;
  /** The person's own words, verbatim. */
  quote: string;
  /** Link to the original post or comment. */
  url: string;
  /** Avatar image URL. When absent the page shows the name's first letter. */
  avatar?: string;
  /** One line saying what the quote is about. */
  context?: string;
};

export const voices: Voice[] = [
  {
    name: 'Young Russell',
    handle: '@russodooope',
    platform: 'Threads',
    date: '2026-11-06',
    device: '索尼电视',
    quote:
      '今天实在受不了索尼电视的投屏软件，之前乐播要会员，换了当贝投屏，现在看15分钟也要收费。问了下 AI推荐了两个开源工具：CastBay，免费投屏，支持 AirPlay、DLNA。简单试了一下基本没问题，就是 iPhone 切换 App 时偶尔会断开，但至少没广告、不收费。',
    url: 'https://www.threads.com/@russodooope/post/DfbO6FuI3Px',
    context: '推荐 CastBay 替代乐播和当贝（原帖还推荐了 TV Bro）。',
  },
  {
    name: 'Meloo/资源记录库',
    handle: '@BlackMelooo',
    platform: 'X',
    date: '2026-11-21',
    quote:
      'AirPlay 不只是苹果专属。\n\nCastBay 可以让家里的小米电视、安卓电视盒子，安卓车机，安装这个软件后，就可以接收苹果设备的无线投屏。\n\n甚至Windows 和 Linux 电脑也能把屏幕或指定窗口投过来。免费、开源、无广告。\n\n不过说实话 AirPlay 确实挺好用的。iOS生态中，用的方便的不行。',
    url: 'https://x.com/BlackMelooo/status/2122216503756136885',
    context: '在 X 上推荐 CastBay。',
  },
  {
    name: '火工道人',
    platform: 'ZNDS',
    date: '2026-11-21',
    quote: '感谢分享！前一阵我还发帖推荐了没想到作者本人也来了',
    url: 'https://www.znds.com/tv-1275175-1-1.html',
    context: '他曾在 2026-11-03 发帖推荐 CastBay（《【分享】映湾 CastBay：一款免费的开源 AirPlay 和 DLNA 接收器》，10,172 次浏览）。',
  },
  {
    name: 'miozx',
    platform: 'GitHub',
    date: '2026-11-24',
    device: 'Android 6.0 未 root 电视',
    quote:
      '已升级 1.10.2 验证两点：开关开启时重启电视，CastBay 正常自启（进程存在，DLNA 端口 49152 监听中），与升级前行为一致；开关关闭后重启电视，不再自启。感谢这么快实现。',
    url: 'https://github.com/weenas/castbay/issues/399#issuecomment-7126528816',
    avatar: 'https://github.com/miozx.png',
    context: '验证 1.10.2 的开机自启开关（开、关两种状态都正常），随后自己关闭了 issue #399。',
  },
  {
    name: 'andynext365',
    platform: 'GitHub',
    date: '2026-11-12',
    device: 'AIUniBOX-M11 电视盒子',
    quote: '今天试了一下，非常流畅，目前没有报错，很棒',
    url: 'https://github.com/weenas/castbay/issues/214#issuecomment-6811827325',
    avatar: 'https://github.com/andynext365.png',
    context: '复测 issue #214 的启动报错，确认已修好。',
  },
  {
    name: 'yfanswer',
    platform: 'GitHub',
    date: '2026-11-01',
    device: '索尼电视 + iPhone',
    quote: '刚刚简单验证了一下，没有再暂停，后续我再多试试，有问题再反馈。',
    url: 'https://github.com/weenas/castbay/issues/352#issuecomment-6556529453',
    avatar: 'https://github.com/yfanswer.png',
    context: '验证 1.9.0 修好了索尼电视直播投屏反复暂停（issue #352）。',
  },
  {
    name: 'wddd27',
    platform: 'GitHub',
    date: '2026-11-01',
    device: 'Rockchip RK3566 设备',
    quote: '问题已经完美解决，感谢～',
    url: 'https://github.com/weenas/castbay/issues/356#issuecomment-6557769099',
    avatar: 'https://github.com/wddd27.png',
    context: '验证 1.9.0 修好了投屏黑屏（issue #356）。',
  },
  {
    name: 'sawino',
    platform: 'GitHub',
    date: '2026-11-24',
    quote:
      "The setting of making Fit as default saves me from switching the mode for every projection. It's what i need because i basically use mirroring for Videos without a TV button. I'll make a post about your product in case people like me need it.",
    url: 'https://github.com/weenas/castbay/discussions/408#discussioncomment-19854718',
    avatar: 'https://github.com/sawino.png',
    context: '确认把 Fit（适应）作为默认画面模式正是他需要的，并表示会发帖推荐。',
  },
];
