<script lang="ts">
	import { onMount } from 'svelte'
	import { entryName, loadCatalog } from '../lib/catalog.ts'
	import { V, W, barFraction, relDay } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { F, getBoard, getLeague, loadFriends, loadNews, type BoardRow, type LeagueRow } from '../lib/social.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import FriendsList from '../components/FriendsList.svelte'
	import Icon from '../components/Icon.svelte'
	import Podium from '../components/Podium.svelte'

	const m = $derived(getModel())
	let league = $state<LeagueRow[] | null>(null)
	let names = $state(new Map<string, string>())
	onMount(() => {
		void loadFriends(); void loadNews()
		void getLeague().then((r) => (league = r))
		void loadCatalog().then((c) => (names = new Map(c.map((e) => [e.id, entryName(e)]))))
	})

	// ---- la lega: una metrica alla volta ----
	type Metric = 'volume30' | 'sessions30' | 'streak'
	let metric = $state<Metric>('volume30')
	const fmt = (r: LeagueRow, k: Metric) => (k === 'volume30' ? V(r[k]) : String(r[k]))
	const ranked = $derived([...(league ?? [])].sort((a, b) => b[metric] - a[metric] || Number(b.tu) - Number(a.tu)))
	const podium = $derived(ranked.slice(0, 3).map((r) => ({ id: r.id, name: r.tu ? t('Tu') : '@' + r.username, value: fmt(r, metric), amount: r[metric], you: r.tu })))
	const top = $derived(Math.max(1, ...ranked.map((r) => r[metric])))
	const myRank = $derived(ranked.findIndex((r) => r.tu) + 1)
	const hasFriends = $derived((F.friends?.amici.length ?? 0) > 0)
	const METRICS: { k: Metric; label: string }[] = [{ k: 'volume30', label: t('Volume 30 giorni') }, { k: 'sessions30', label: t('Allenamenti 30 giorni') }, { k: 'streak', label: t('Settimane di fila') }]

	// ---- per esercizio: l'1RM stimato, solo per i macchinari del catalogo ----
	const mine = $derived(m.exercises.filter((e) => e.catalog && !e.isArchived && e.best))
	let pick = $state('')
	let board = $state<BoardRow[] | null>(null)
	const current = $derived(pick || mine[0]?.catalog || '')
	$effect(() => { const c = current; board = null; if (c && hasFriends) void getBoard(c).then((r) => { if (c === current) board = r }) })
	const boardTop = $derived(Math.max(1, ...(board ?? []).map((r) => r.oneRm)))
	const news = $derived((F.news ?? []).slice(0, 6))
</script>

<main class="screen page">
	<div class="bar span"><h1>{t('Amici')}</h1></div>

	<!-- a schermo largo: a sinistra cerca e lega, a destra esercizio per esercizio e novità; a tre colonne la sinistra si divide in due -->
	<div class="blk fan">
		<div class="blk"><FriendsList /></div>

		{#if !hasFriends}
			<div class="card empty-state">
				<div class="big-icon"><Icon name="users" size={44} /></div>
				<div class="mid">{t('Allenarsi è più bello insieme')}</div>
				<p class="muted small">{t('Invita un amico con il link o il QR, oppure cerca il suo username. Vedrete i massimali e le schede l\'uno dell\'altro, e potrete fare la classifica.')}</p>
			</div>
		{:else if league}
			<div class="blk">
				<div class="sec-title">{t('La lega')}</div>
				<div class="card">
					<div class="chips" style="margin-bottom:14px">{#each METRICS as x}<button class="chip" class:on={metric === x.k} onclick={() => (metric = x.k)}>{x.label}</button>{/each}</div>
					<Podium rows={podium} onpick={(id) => go(`amici/${id}`)} />
					{#if myRank}<p class="small muted" style="text-align:center;margin:14px 0 4px">{myRank === 1 ? t('Sei in testa. Tienila stretta.') : t('Sei {0}° su {1}.', myRank, ranked.length)}</p>{/if}
				</div>
				<div class="card list">
					{#each ranked as r, i (r.id)}
						<button class="item" class:me={r.tu} onclick={() => !r.tu && go(`amici/${r.id}`)}>
							<b class="rk num">{i + 1}</b>
							<div class="grow" style="min-width:0">
								<div class="row between"><b class="ellipsis">{r.tu ? t('Tu') : '@' + r.username}</b><b class="num">{fmt(r, metric)}</b></div>
								<div class="hbar" style="margin-top:6px"><div style="width:{barFraction(r[metric], top) * 100}%;background:{r.tu ? 'var(--accent)' : 'rgba(255,255,255,.35)'}"></div></div>
								<div class="tiny faint" style="margin-top:4px"><Icon name="flame" size={11} /> {r.streak} · {r.last ? relDay(r.last) : t('Mai allenato')}</div>
							</div>
						</button>
					{/each}
				</div>
			</div>
		{/if}
	</div>

	{#if hasFriends && league}
		<div class="blk">
			{#if mine.length}
				<div class="blk">
					<div class="sec-title">{t('Esercizio per esercizio')}</div>
					<div class="card">
						<div class="chips scroll" style="margin-bottom:12px">{#each mine as e (e.uuid)}<button class="chip" class:on={current === e.catalog} onclick={() => (pick = e.catalog)}>{names.get(e.catalog) ?? e.name}</button>{/each}</div>
						{#if !board}<div class="empty">…</div>
						{:else if board.length < 2}<p class="small muted">{t('Nessun amico ha ancora questo esercizio.')}</p>
						{:else}
							<div class="stack" style="gap:10px">
								{#each board as r, i (r.id)}
									<button class="brow" onclick={() => !r.tu && go(`amici/${r.id}`)}>
										<div class="row between"><span class="row" style="gap:10px"><b class="num" style="width:18px;color:{i === 0 ? '#ffd45e' : 'var(--text-3)'}">{i + 1}</b><span style="font-weight:{r.tu ? 800 : 600}">{r.tu ? t('Tu') : '@' + r.username}</span></span><span class="num"><b>{W(r.oneRm)}</b> <span class="small muted">{W(r.weight)} × {r.reps}</span></span></div>
										<div class="hbar" style="margin-top:6px"><div style="width:{barFraction(r.oneRm, boardTop) * 100}%;background:{r.tu ? 'var(--accent)' : 'rgba(255,255,255,.35)'}"></div></div>
									</button>
								{/each}
							</div>
							<p class="tiny faint" style="margin-top:10px">{t('1RM stimato dalle serie migliori.')}</p>
						{/if}
					</div>
				</div>
			{/if}

			<div class="blk">
				<div class="sec-title">{t('Novità')}</div>
				{#if news.length}
					<div class="card list">
						{#each news as e, i (e.id + e.catalog + e.date + i)}
							<button class="item" onclick={() => go(`amici/${e.id}`)}>
								<div class="grow ellipsis"><b>@{e.username}</b> · {names.get(e.catalog) ?? e.name}<div class="small muted">{relDay(e.date)}</div></div>
								<div style="text-align:right"><b class="num">{W(e.weight)} × {e.reps}</b>{#if e.sorpasso}<div class="small" style="color:var(--neg)">{t('Ti ha superato')}</div>{/if}</div>
							</button>
						{/each}
					</div>
				{:else}<div class="empty">{t('Nessun record dei tuoi amici negli ultimi giorni.')}</div>{/if}
			</div>
			<p class="small faint" style="text-align:center;margin-top:16px">{tn(F.friends?.amici.length ?? 0, '1 amico', '{0} amici')}</p>
		</div>
	{/if}
</main>

<style>
	.empty-state { text-align: center; padding: 28px 20px; }
	@media (min-width: 900px) and (max-width: 1399.98px) { .empty-state { margin-top: 20px; } }
	.big-icon { width: 84px; height: 84px; margin: 0 auto 14px; border-radius: 50%; display: grid; place-items: center; color: var(--accent);
		background: radial-gradient(circle, rgba(var(--accent-rgb), .25), rgba(var(--accent-rgb), .05)); }
	.rk { width: 22px; text-align: center; color: var(--text-3); flex: none; }
	.item.me { background: rgba(var(--accent-rgb), .08); }
	.item.me .rk { color: var(--accent); }
	.chips.scroll { flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none; margin: 0 -4px 12px; padding: 0 4px; }
	.brow { border: 0; background: none; color: inherit; text-align: left; padding: 0; width: 100%; }
</style>
