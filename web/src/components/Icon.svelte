<script lang="ts">
	const paths: Record<string, string> = {
		trophy: '<path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0V4zM17 5h3v2a3 3 0 0 1-3 3M7 5H4v2a3 3 0 0 0 3 3"/>',
		dumbbell: '<path d="M6.5 6.5v11M17.5 6.5v11M3 9v6M21 9v6M6.5 12h11"/>',
		chart: '<path d="M3 3v18h18M7 15l4-4 3 3 5-6"/>',
		sliders: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
		plus: '<path d="M12 5v14M5 12h14"/>',
		left: '<path d="M15 18l-6-6 6-6"/>',
		right: '<path d="M9 18l6-6-6-6"/>',
		x: '<path d="M6 6l12 12M18 6L6 18"/>',
		check: '<path d="M5 12l5 5L20 7"/>',
		search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
		trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
		edit: '<path d="M4 20h4L19 9l-4-4L4 16v4z"/>',
		camera: '<path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="4"/>',
		timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2 2M9 2h6"/>',
		flame: '<path d="M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3-1-6 1-9z"/>',
		repeat: '<path d="M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3"/>',
		more: '<circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>',
		archive: '<path d="M3 5h18v4H3zM5 9v10h14V9M10 13h4"/>',
		download: '<path d="M12 3v12M7 11l5 5 5-5M4 21h16"/>',
		upload: '<path d="M12 17V5M7 9l5-5 5 5M4 21h16"/>',
		phone: '<rect x="7" y="2" width="10" height="20" rx="2.5"/><path d="M11 18h2"/>',
		cloud: '<path d="M7 18a4 4 0 0 1-.5-7.97A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z"/>',
		scale: '<rect x="4" y="4" width="16" height="16" rx="4"/><path d="M8 9a5 5 0 0 1 8 0l-2 2h-4z"/>'
	}
	let { name, size = 24 }: { name: string; size?: number } = $props()
</script>

<svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{@html paths[name] ?? ''}</svg>
