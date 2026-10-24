// Come si installa Peak su questo dispositivo, dallo user agent. Pura: la prova sta in install.test.ts.
// `hasPrompt`: il browser ha già emesso beforeinstallprompt (Chrome, Edge, Samsung Internet), quindi basta un tasto.
export type InstallTarget = 'ios-safari' | 'ios-other' | 'android-prompt' | 'android-samsung' | 'android-firefox' | 'android-other' | 'desktop-prompt'

export function installTarget(ua: string, maxTouchPoints: number, hasPrompt: boolean): InstallTarget | null {
  // iPadOS si presenta come Mac, ma ha il touch
  if (/iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && maxTouchPoints > 1)) return /CriOS|FxiOS|EdgiOS|OPiOS/.test(ua) ? 'ios-other' : 'ios-safari'
  if (/Android/.test(ua)) return hasPrompt ? 'android-prompt' : /SamsungBrowser/.test(ua) ? 'android-samsung' : /Firefox/.test(ua) ? 'android-firefox' : 'android-other'
  return hasPrompt ? 'desktop-prompt' : null // il desktop senza prompt (Safari, Firefox) non ha un'installazione da proporre
}
