// `on` accende e spegne l'avviso; testo e icona restano finché non ne arriva un altro, così mentre scende (transizione d'uscita) non si svuota
export const T = $state({ msg: '', id: 0, icon: '', on: false })
let timer: ReturnType<typeof setTimeout> | undefined

/** `icon`: il nome di un'icona di Icon.svelte, a sinistra del testo. */
export function toast(msg: string, ms = 2600, icon = '') {
	T.msg = msg
	T.icon = icon
	T.id++
	T.on = true
	clearTimeout(timer)
	timer = setTimeout(() => { T.on = false }, ms)
}
