export const T = $state({ msg: '', id: 0, icon: '' })
let timer: ReturnType<typeof setTimeout> | undefined

/** `icon`: il nome di un'icona di Icon.svelte, a sinistra del testo. */
export function toast(msg: string, ms = 2600, icon = '') {
	T.msg = msg
	T.icon = icon
	T.id++
	clearTimeout(timer)
	timer = setTimeout(() => { T.msg = ''; T.icon = '' }, ms)
}
