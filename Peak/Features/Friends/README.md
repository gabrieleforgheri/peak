# Features/Friends

## Overview
Schermate sociali di Peak: classifica fra amici, profilo di un amico e invito. I dati arrivano da `SocialStore` e da `PeakAPI` (`Store/PeakSocial.swift`); qui non c'è persistenza locale.

## Interface
- `FriendsView`: elenco amici, richieste, classifica (`Metric`), `Podium` / `PodiumEntry`, `ProgressBar`.
- `FriendProfileView`: profilo di un amico (`FriendProfileData`), massimali e schede condivise.
- `InviteSheet`: invito di un nuovo amico.
- Dipende da [Store](../../Store/README.md) (`SocialStore`, `FriendPerson`, `LeagueRow`, `SharedMax`) e da [Design](../../Design/README.md).

## Invariants
- Le viste leggono lo stato sociale solo da `SocialStore`/`PeakAPI`; non hanno persistenza propria.
- Senza account collegato (`SyncService.State != .connected`) la funzione non ha dati da mostrare.

## Side Effects
- Chiamate di rete a peak.yrb4g.com tramite `PeakAPI` (richieste, accettazioni, inviti).

## Verification
- Run: `xcodebuild test -scheme Peak -destination 'id=<iPhone UDID>' -allowProvisioningUpdates` (solo iPhone fisico, mai simulatore; non eseguirlo su questo Mac senza chiedere).
- Controllo di compilazione senza esecuzione: `swiftc -typecheck` sui sorgenti di `Peak/`, `Shared/` e `WatchShared/` (percorsi dalla radice del repo).
