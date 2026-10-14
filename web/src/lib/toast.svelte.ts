export const T = $state({ msg: '', id: 0 })
let timer: ReturnType<typeof setTimeout> | undefined

export function toast(msg: string, ms = 2600) {
	T.msg = msg
	T.id++
	clearTimeout(timer)
	timer = setTimeout(() => { T.msg = '' }, ms)
}
