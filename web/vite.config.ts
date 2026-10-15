import { defineConfig, type Plugin } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'

// In sviluppo /api e /auth vanno a un server Peak locale, così il cookie vale come in produzione.
const peak = process.env.PEAK ?? 'http://127.0.0.1:3016'

/** sw.js con la lista dei file del guscio, che cambia a ogni build. */
function serviceWorker(): Plugin {
	return {
		name: 'peak-sw',
		apply: 'build',
		generateBundle(_, bundle) {
			const files = ['/', ...Object.keys(bundle).map((f) => `/${f}`), '/manifest.webmanifest', '/icon-192.png']
			const version = createHash('sha256').update(files.join() + Object.values(bundle).map((b) => ('code' in b ? b.code : '')).join()).digest('hex').slice(0, 10)
			const source = readFileSync('sw.js', 'utf8').replace('__SHELL__', JSON.stringify(files)).replace('__VERSION__', JSON.stringify(version))
			this.emitFile({ type: 'asset', fileName: 'sw.js', source })
		}
	}
}

export default defineConfig({
	// la versione dell'app (da package.json: tenerla uguale a MARKETING_VERSION di project.yml)
	define: { __APP_VERSION__: JSON.stringify(JSON.parse(readFileSync('package.json', 'utf8')).version) },
	plugins: [svelte(), serviceWorker()],
	resolve: { alias: { '@common': fileURLToPath(new URL('../common', import.meta.url)) } },
	server: { proxy: { '/api': peak, '/auth': peak }, fs: { allow: ['..'] } },
	build: { target: 'safari16', assetsInlineLimit: 0, outDir: 'dist', emptyOutDir: true }
})
