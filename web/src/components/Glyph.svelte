<script lang="ts">
	import { glyphSvg } from '@common/glyph.ts'
	import type { GlyphName } from '@common/docs.ts'
	let { name }: { name: GlyphName } = $props()
</script>

<svg viewBox="0 0 1 1" aria-hidden="true">{@html glyphSvg(name)}</svg>
