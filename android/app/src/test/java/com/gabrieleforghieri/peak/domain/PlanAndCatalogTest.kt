package com.gabrieleforghieri.peak.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ProgramTests.swift. */
class ProgramTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    /** Stessi esempi di common/plan.test.ts: l'icona dedotta dal nome è la stessa sul web e qui. */
    @Test fun iconSuggestedFromName() {
        assertEquals("muscle:petto", TemplateIcon.suggest("Push 1"))
        assertEquals("muscle:schiena", TemplateIcon.suggest("Pull"))
        assertEquals("muscle:gambe", TemplateIcon.suggest("Gambe"))
        assertEquals("heart", TemplateIcon.suggest("Cardio"))
        assertEquals("", TemplateIcon.suggest("Giorno A"))
        assertEquals("star", TemplateIcon.resolved("star", "Push"))
        assertEquals("muscle:spalle", TemplateIcon.suggest("Spalle e Trapezio"))
        assertEquals("muscle:gambe", TemplateIcon.suggest("Glutei"))   // senza accenti si legge uguale
    }

    private fun day(name: String, weekday: Int, p: String) = DayRef(uuid(name.hashCode()), name, p, weekday)

    @Test fun todayOnlyWithFixedDays() {
        val program = ProgramRef(uuid(1), useDays = false)
        val days = listOf(day("Push", 0, program.uuid), day("Pull", 1, program.uuid))
        val monday = at(2026, 10, 26)
        val sunday = at(2026, 11, 1)
        assertNull(Programs.today(program, days, monday, zone))   // giorni non in uso
        val used = program.copy(useDays = true)
        assertEquals("Push", Programs.today(used, days, monday, zone)?.name)
        assertNull(Programs.today(used, days, sunday, zone))   // riposo
        assertNull(Programs.today(null, days, monday, zone))   // nessuna scheda
    }

    /** Domenica è 6, un giorno di un'altra scheda non conta, con due giorni sullo stesso giorno vince il primo. */
    @Test fun todayEdgeCases() {
        val program = ProgramRef(uuid(1), useDays = true)
        val other = ProgramRef(uuid(2), useDays = false)
        val sunday = at(2026, 11, 1, 23, 59)
        val templates = listOf(day("Altra", 6, other.uuid), day("Lunga", 6, program.uuid), day("Doppia", 6, program.uuid), day("Senza", -1, program.uuid))
        assertEquals("Lunga", Programs.today(program, templates, sunday, zone)?.name)
        assertNull(Programs.today(other, templates, sunday, zone))   // B non usa i giorni
    }

    @Test fun activeProgramFallsBackToTheFirst() {
        val list = listOf(uuid(1), uuid(2))
        assertEquals(uuid(2), Programs.active(list, { it }, uuid(2).lowercase()))
        assertEquals(uuid(1), Programs.active(list, { it }, ""))
        assertNull(Programs.active(emptyList<String>(), { it }, ""))
    }
}

/** TemplateTests.swift (la parte di piano e righe; il backup è della fase 2) e common/plan.test.ts. */
class TemplateTest {
    private val a = uuid(10)
    private val b = uuid(11)
    private val c = uuid(12)
    private fun item(id: String, sets: Int = 4) = TemplateItem(id, sets, 8, 10, 60.0)

    @Test fun itemsRoundTripThroughJsonAndTheServerDocument() {
        val items = listOf(item(a), TemplateItem(b))
        assertEquals(items, TemplateItems.decode(TemplateItems.encode(items)))
        assertEquals("", TemplateItems.encode(emptyList()))
        assertEquals(items, TemplateItems.parse(TemplateItems.json(items)))
        // una riga rotta si salta, le altre restano
        val rows = kotlinx.serialization.json.JsonArray(
            listOf(kotlinx.serialization.json.JsonObject(mapOf("exercise" to kotlinx.serialization.json.JsonPrimitive("non-un-uuid")))) + TemplateItems.json(listOf(item(a))),
        )
        assertEquals(listOf(item(a)), TemplateItems.parse(rows))
        assertEquals(emptyList<TemplateItem>(), TemplateItems.decode("non è json"))
    }

    @Test fun sessionOrderAndModes() {
        val sets = listOf(PlanSet(c, 30.0, 10, false), PlanSet(a, 60.0, 8, false))
        val gone = uuid(99)
        val plan = listOf(item(a), item(gone), item(b))
        val exists = { id: String -> id != gone }

        assertEquals(listOf(a, b, c), Plan.session(plan, sets, exists).map { it.exercise })
        assertEquals(Plan.blank(c), Plan.session(plan, sets, exists)[2])
        assertEquals("gli obiettivi scritti restano", item(a), Plan.session(plan, sets, exists, Plan.Mode.targets)[0])
        assertEquals(Plan.item(sets, c), Plan.session(plan, sets, exists, Plan.Mode.targets)[2])
        assertEquals(Plan.item(sets, a), Plan.session(plan, sets, exists, Plan.Mode.done)[0])
        assertEquals("senza serie resta l'obiettivo", item(b), Plan.session(plan, sets, exists, Plan.Mode.done)[1])
    }

    @Test fun itemFromSetsSummarisesWorkingSets() {
        val sets = listOf(PlanSet(a, 60.0, 10, false), PlanSet(a, 62.5, 8, false), PlanSet(a, 20.0, 15, true))
        assertEquals(TemplateItem(a, 2, 8, 10, 62.5), Plan.item(sets, a))
        assertEquals(Plan.blank(b), Plan.item(sets, b))
    }

    @Test fun editingTheList() {
        val list = listOf(item(a), item(b), item(c))
        assertEquals(4, Plan.adding(list, uuid(77)).size)
        assertEquals(list, Plan.adding(list, a))
        assertEquals(listOf(a, c), Plan.removing(list, b).map { it.exercise })
        val x = uuid(55)
        assertEquals(TemplateItem(x, 4, 8, 10, null), Plan.replacing(list, b, x)[1])
        assertEquals(list, Plan.replacing(list, b, c))
        assertEquals(listOf(a, c, b), Plan.moving(list, c, -1).map { it.exercise })
        assertEquals(list, Plan.moving(list, a, -1))
    }

    @Test fun differsIgnoresTargets() {
        val template = listOf(item(a), item(b))
        assertFalse(Plan.differs(listOf(item(a, 1), item(b, 9)), template) { true })
        assertTrue(Plan.differs(listOf(item(b), item(a)), template) { true })
        assertFalse(Plan.differs(listOf(item(a)), template) { it == a })
    }

    /** WorkoutFocusTests.swift: il filtro «cosa alleni oggi» passa dal testo, in ordine canonico. */
    @Test fun focusRoundTripsThroughRawValue() {
        assertTrue(WorkoutFocus.decode("").isEmpty())
        assertEquals("petto,spalle", WorkoutFocus.encode(setOf(MuscleGroup.spalle, MuscleGroup.petto)))
        assertEquals(setOf(MuscleGroup.petto, MuscleGroup.spalle), WorkoutFocus.decode("petto,spalle"))
        assertEquals("", WorkoutFocus.encode(emptySet()))
        assertEquals(setOf(MuscleGroup.gambe), WorkoutFocus.decode("gambe,boh"))
    }

    @Test fun targetLineShowsOnlyFilledFields() {
        assertEquals("2/4 serie · 8–10 rip · 60 kg", Fmt.targetLine(TemplateItem(a, 4, 8, 10, 60.0), 2, null, WeightUnit.kg, java.util.Locale.ITALY))
        assertEquals("8 rip · ultimo 52,5 kg", Fmt.targetLine(TemplateItem(a, 0, 8, 8, null), 0, 52.5, WeightUnit.kg, java.util.Locale.ITALY))
        assertEquals("", Fmt.targetLine(TemplateItem(a), 0, null, WeightUnit.kg))
    }
}

/** CatalogTests.swift: gli stessi casi di common/catalog.test.ts, sul catalogo vero. */
class CatalogTest {
    init { TestCatalog.load() }

    @Test fun idsAreUniqueAndWellFormed() {
        val seen = HashSet<String>()
        for (e in Catalog.entries) {
            assertTrue(e.id, Regex("^[a-z0-9]+(-[a-z0-9]+)*$").matches(e.id))
            assertTrue("id doppio ${e.id}", seen.add(e.id))
            assertNotNull(e.id, MuscleGroup.of(e.group))
            assertTrue(e.step > 0)
        }
        assertTrue(Catalog.entries.size >= 250)
    }

    @Test fun normalization() {
        assertEquals("leg press 45", Catalog.norm("  Leg Press 45° "))
        assertEquals("farmer s walk", Catalog.norm("Farmer's Walk"))
        assertEquals("perche", Catalog.norm("Perché"))
    }

    @Test fun matchByNameFindsItalianEnglishAndSynonyms() {
        assertEquals("bench-press-barbell", Catalog.match("panca piana bilanciere")?.id)
        assertEquals("bench-press-barbell", Catalog.match("Barbell Bench Press")?.id)
        assertEquals("bench-press-barbell", Catalog.match("bench press")?.id)
        assertNull(Catalog.match("Panca"))
        assertNull(Catalog.match("Il mio macchinario"))
        assertNull(Catalog.match("   "))
    }

    @Test fun searchOrdersByPrefixAndAcceptsAnyWordOrder() {
        assertEquals("bench-press-barbell", Catalog.search("bilanciere panca").first().id)
        assertEquals("pec-deck", Catalog.search("butterfly").first().id)
        assertTrue(Catalog.search("xyzzy").isEmpty())
        assertEquals(5, Catalog.search("", limit = 5).size)
    }

    /** Il gruppo filtra prima del limite; ricerca vuota + gruppo = tutto il gruppo. */
    @Test fun groupFiltersBeforeTheLimit() {
        val all = Catalog.search("", MuscleGroup.gambe)
        assertTrue(all.size > 40)
        assertTrue(all.all { it.muscleGroup == MuscleGroup.gambe })
        assertTrue(Catalog.search("press", MuscleGroup.gambe, limit = 3).size <= 3)
    }

    @Test fun everyOldCatalogNameStillLinks() {
        for (name in listOf("Chest Press", "Leg Press 45°", "Stacco da Terra", "Plank", "Curl ai Polsi", "Pectoral Machine")) {
            assertNotNull(name, Catalog.match(name))
        }
    }

    /** MachineGlyphTests.swift. */
    private fun glyph(name: String, group: MuscleGroup) = MachineGlyph.suggested(name, group)

    @Test fun recognisesCommonMachines() {
        assertEquals(MachineGlyph.legPress, glyph("Leg Press 45°", MuscleGroup.gambe))
        assertEquals(MachineGlyph.latPulldown, glyph("Lat Machine", MuscleGroup.schiena))
        assertEquals(MachineGlyph.seatedRow, glyph("Pulley Basso", MuscleGroup.schiena))
        assertEquals(MachineGlyph.pecDeck, glyph("Pectoral Machine", MuscleGroup.petto))
        assertEquals(MachineGlyph.cableCross, glyph("Croci ai Cavi", MuscleGroup.petto))
        assertEquals(MachineGlyph.romanChair, glyph("Sedia Romana", MuscleGroup.core))
        assertEquals(MachineGlyph.smithMachine, glyph("Squat al Multipower", MuscleGroup.gambe))
    }

    /** Le voci più specifiche devono vincere su quelle generiche. */
    @Test fun specificNamesWinOverGenericOnes() {
        assertEquals(MachineGlyph.seatedRow, glyph("Rematore Machine", MuscleGroup.schiena))
        assertEquals(MachineGlyph.barbell, glyph("Rematore Bilanciere", MuscleGroup.schiena))
        assertEquals(MachineGlyph.dumbbell, glyph("Rematore con Manubrio", MuscleGroup.schiena))
        assertEquals(MachineGlyph.cableStation, glyph("Rematore ai Cavi", MuscleGroup.schiena))
        assertEquals(MachineGlyph.legPress, glyph("Hack Squat", MuscleGroup.gambe))
        assertEquals(MachineGlyph.squatRack, glyph("Squat con Bilanciere", MuscleGroup.gambe))
        assertEquals(MachineGlyph.plateLoaded, glyph("Chest Press Plate Loaded", MuscleGroup.petto))
    }

    @Test fun glyphIsCaseInsensitiveAndFallsBackToTheGroup() {
        assertEquals(MachineGlyph.legPress, glyph("LEG PRESS", MuscleGroup.gambe))
        assertEquals(MachineGlyph.latPulldown, glyph("lat machine presa stretta", MuscleGroup.schiena))
        assertEquals(MachineGlyph.bodyweight, glyph("Attrezzo Sconosciuto", MuscleGroup.core))
        assertEquals(MachineGlyph.legLever, glyph("Attrezzo Sconosciuto", MuscleGroup.gambe))
        assertEquals(MachineGlyph.seatedRow, glyph("Attrezzo Sconosciuto", MuscleGroup.schiena))
        assertEquals(MachineGlyph.seatedPush, glyph("", MuscleGroup.petto))
    }

    /** Ogni voce del catalogo deve avere un pittogramma e un disegno non vuoto. */
    @Test fun everyCatalogEntryHasADrawableGlyph() {
        for (e in Catalog.entries) {
            assertNotNull("${e.id}: pittogramma sconosciuto", MachineGlyph.of(e.glyph))
            assertFalse("${e.nameIt} senza disegno", e.machineGlyph.shapes.isEmpty())
            assertTrue(e.machineGlyph.title.isNotEmpty())
            for (m in e.muscleIDs) assertNotNull(m)
        }
    }

    @Test fun everyGlyphStaysInsideItsBox() {
        for (g in MachineGlyph.entries) for (s in g.shapes) {
            val coords = when (s) {
                is GlyphShape.Line -> listOf(s.x1, s.y1, s.x2, s.y2)
                is GlyphShape.Bar -> listOf(s.x1, s.y1, s.x2, s.y2)
                is GlyphShape.SolidRect -> listOf(s.x, s.y, s.x + s.w, s.y + s.h)
                is GlyphShape.Dot -> listOf(s.cx - s.r, s.cy - s.r, s.cx + s.r, s.cy + s.r)
                is GlyphShape.Ring -> listOf(s.cx - s.r, s.cy - s.r, s.cx + s.r, s.cy + s.r)
            }
            for (v in coords) assertTrue("${g.raw} esce dal riquadro", v in 0f..1f)
        }
    }

    /** Il primo avvio propone solo voci che esistono nel catalogo (common/onboarding.test.ts). */
    @Test fun onboardingSuggestionsExistInTheCatalog() {
        for ((group, ids) in Onboarding.suggested) {
            assertEquals(5, ids.size)
            for (id in ids) assertEquals("$group/$id", group.raw, Catalog.entry(id)?.group)
        }
    }

    @Test fun onboardingBuildsOneDayPerGroupAndReusesOwnedMachines() {
        val chest = Onboarding.suggested.getValue(MuscleGroup.petto).take(3).map { Catalog.entry(it)!! }
        val days = listOf(Onboarding.Day(MuscleGroup.petto, "Petto", chest), Onboarding.Day(MuscleGroup.core, "Core", listOf(Catalog.entry("pec-deck")!!)))
        var n = 100
        val owned = listOf(Onboarding.Owned(uuid(1), "pec-deck", isArchived = false, sortIndex = 4), Onboarding.Owned(uuid(2), "chest-press-machine", isArchived = true, sortIndex = 5))
        val plan = Onboarding.build("La mia scheda", days, owned, WeightUnit.kg, newUuid = { uuid(n++) })
        assertEquals(listOf("muscle:petto", "muscle:core"), plan.days.map { it.icon })
        assertEquals(listOf(0, 1), plan.days.map { it.sortIndex })
        // pec-deck lo hai già: non si ricrea; chest-press archiviato sì
        assertEquals(setOf("chest-press-machine", "incline-chest-press-machine"), plan.exercises.map { it.entry.id }.toSet())
        assertEquals(listOf(6, 7), plan.exercises.map { it.sortIndex })
        assertTrue(plan.days[0].items.any { it.exercise == uuid(1) })
        assertEquals(plan.days[0].items.size, plan.days[0].items.map { it.exercise }.toSet().size)
    }
}
