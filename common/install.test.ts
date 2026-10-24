import { test } from 'node:test'
import assert from 'node:assert/strict'
import { installTarget as it } from './install.ts'

const SAFARI = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1'
const MAC = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15'
const CHROME_AND = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36'

test('installazione: dispositivo e browser', () => {
  assert.equal(it(SAFARI, 5, false), 'ios-safari')
  assert.equal(it(SAFARI.replace('Version/17.4', 'CriOS/124.0'), 5, false), 'ios-other')
  assert.equal(it(MAC, 5, false), 'ios-safari') // iPad
  assert.equal(it(MAC, 0, false), null) // Safari su Mac
  assert.equal(it(CHROME_AND, 5, true), 'android-prompt')
  assert.equal(it(CHROME_AND, 5, false), 'android-other')
  assert.equal(it(CHROME_AND + ' SamsungBrowser/24.0', 5, false), 'android-samsung')
  assert.equal(it('Mozilla/5.0 (Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0', 5, false), 'android-firefox')
  assert.equal(it('Mozilla/5.0 (X11; Linux x86_64) Chrome/124.0 Safari/537.36', 0, true), 'desktop-prompt')
  assert.equal(it('Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Firefox/125.0', 0, false), null)
})
