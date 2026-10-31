// Generato da android/scripts/gen-assets.ts: non modificarlo a mano.

package com.gabrieleforghieri.peak.domain

/** Pittogrammi degli attrezzi: uno per tipo di attrezzo del catalogo, più quelli generici di prima. */
enum class MachineGlyph(val raw: String, val title: String, val shapes: List<GlyphShape>) {
    seatedPush("seatedPush", "Spinta da seduto", listOf(
        SolidRect(0.06f, 0.26f, 0.14f, 0.54f),
        Line(0.06f, 0.395f, 0.2f, 0.395f),
        Line(0.06f, 0.53f, 0.2f, 0.53f),
        Line(0.06f, 0.665f, 0.2f, 0.665f),
        Line(0.28f, 0.14f, 0.28f, 0.88f),
        SolidRect(0.37f, 0.3f, 0.075f, 0.32f),
        SolidRect(0.37f, 0.62f, 0.26f, 0.07f),
        Line(0.46f, 0.3f, 0.76f, 0.3f),
        Line(0.46f, 0.44f, 0.76f, 0.44f),
        Dot(0.8f, 0.3f, 0.055f),
        Dot(0.8f, 0.44f, 0.055f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    pecDeck("pecDeck", "Butterfly", listOf(
        SolidRect(0.06f, 0.3f, 0.13f, 0.5f),
        Line(0.06f, 0.425f, 0.19f, 0.425f),
        Line(0.06f, 0.55f, 0.19f, 0.55f),
        Line(0.06f, 0.675f, 0.19f, 0.675f),
        Line(0.3f, 0.16f, 0.3f, 0.88f),
        SolidRect(0.38f, 0.6f, 0.24f, 0.07f),
        Line(0.44f, 0.24f, 0.74f, 0.34f),
        Line(0.44f, 0.52f, 0.74f, 0.42f),
        Dot(0.78f, 0.34f, 0.05f),
        Dot(0.78f, 0.42f, 0.05f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    latPulldown("latPulldown", "Lat machine", listOf(
        SolidRect(0.06f, 0.32f, 0.12f, 0.48f),
        Line(0.06f, 0.44f, 0.18f, 0.44f),
        Line(0.06f, 0.56f, 0.18f, 0.56f),
        Line(0.06f, 0.68f, 0.18f, 0.68f),
        Line(0.24f, 0.12f, 0.24f, 0.88f),
        Line(0.24f, 0.13f, 0.68f, 0.13f),
        Ring(0.64f, 0.19f, 0.05f),
        Line(0.64f, 0.24f, 0.64f, 0.44f),
        Bar(0.38f, 0.46f, 0.92f, 0.46f),
        SolidRect(0.46f, 0.7f, 0.26f, 0.07f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    seatedRow("seatedRow", "Rematore / pulley", listOf(
        SolidRect(0.06f, 0.3f, 0.12f, 0.5f),
        Line(0.06f, 0.425f, 0.18f, 0.425f),
        Line(0.06f, 0.55f, 0.18f, 0.55f),
        Line(0.06f, 0.675f, 0.18f, 0.675f),
        Line(0.24f, 0.16f, 0.24f, 0.88f),
        Line(0.24f, 0.52f, 0.64f, 0.52f),
        Dot(0.68f, 0.52f, 0.055f),
        SolidRect(0.36f, 0.64f, 0.26f, 0.07f),
        Bar(0.84f, 0.52f, 0.84f, 0.8f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    cableStation("cableStation", "Cavi", listOf(
        SolidRect(0.14f, 0.12f, 0.1f, 0.76f),
        Ring(0.19f, 0.22f, 0.045f),
        Line(0.19f, 0.265f, 0.19f, 0.38f),
        Line(0.19f, 0.38f, 0.62f, 0.46f),
        Dot(0.66f, 0.47f, 0.055f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    cableCross("cableCross", "Croci ai cavi", listOf(
        SolidRect(0.07f, 0.14f, 0.09f, 0.74f),
        SolidRect(0.84f, 0.14f, 0.09f, 0.74f),
        Ring(0.115f, 0.24f, 0.04f),
        Ring(0.885f, 0.24f, 0.04f),
        Line(0.115f, 0.28f, 0.5f, 0.54f),
        Line(0.885f, 0.28f, 0.5f, 0.54f),
        Dot(0.5f, 0.57f, 0.055f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    benchFlat("benchFlat", "Panca piana", listOf(
        Bar(0.12f, 0.32f, 0.88f, 0.32f),
        Dot(0.17f, 0.32f, 0.085f),
        Dot(0.83f, 0.32f, 0.085f),
        SolidRect(0.24f, 0.54f, 0.52f, 0.09f),
        Line(0.32f, 0.63f, 0.32f, 0.86f),
        Line(0.68f, 0.63f, 0.68f, 0.86f)
    )),
    benchIncline("benchIncline", "Panca inclinata", listOf(
        Bar(0.12f, 0.26f, 0.88f, 0.26f),
        Dot(0.17f, 0.26f, 0.075f),
        Dot(0.83f, 0.26f, 0.075f),
        Bar(0.26f, 0.74f, 0.66f, 0.46f),
        Line(0.3f, 0.74f, 0.3f, 0.87f),
        Line(0.63f, 0.5f, 0.63f, 0.87f),
        SolidRect(0.2f, 0.72f, 0.16f, 0.07f)
    )),
    barbell("barbell", "Bilanciere", listOf(
        Bar(0.08f, 0.5f, 0.92f, 0.5f),
        Dot(0.2f, 0.5f, 0.145f),
        Dot(0.8f, 0.5f, 0.145f),
        Dot(0.34f, 0.5f, 0.085f),
        Dot(0.66f, 0.5f, 0.085f)
    )),
    dumbbell("dumbbell", "Manubri", listOf(
        Bar(0.33f, 0.5f, 0.67f, 0.5f),
        SolidRect(0.14f, 0.33f, 0.14f, 0.34f),
        SolidRect(0.72f, 0.33f, 0.14f, 0.34f)
    )),
    plateLoaded("plateLoaded", "Plate loaded", listOf(
        Line(0.22f, 0.14f, 0.22f, 0.88f),
        SolidRect(0.28f, 0.58f, 0.24f, 0.07f),
        SolidRect(0.28f, 0.3f, 0.07f, 0.28f),
        Line(0.38f, 0.4f, 0.64f, 0.4f),
        Ring(0.76f, 0.5f, 0.155f),
        Ring(0.76f, 0.5f, 0.05f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    pullUpBar("pullUpBar", "Sbarra / parallele", listOf(
        Line(0.14f, 0.18f, 0.86f, 0.18f),
        Line(0.16f, 0.18f, 0.16f, 0.88f),
        Line(0.84f, 0.18f, 0.84f, 0.88f),
        Dot(0.38f, 0.18f, 0.05f),
        Dot(0.62f, 0.18f, 0.05f),
        Line(0.38f, 0.22f, 0.5f, 0.4f),
        Line(0.62f, 0.22f, 0.5f, 0.4f),
        Dot(0.5f, 0.46f, 0.075f),
        Line(0.5f, 0.53f, 0.5f, 0.74f)
    )),
    legPress("legPress", "Pressa", listOf(
        Bar(0.2f, 0.78f, 0.72f, 0.34f),
        Bar(0.64f, 0.2f, 0.86f, 0.44f),
        SolidRect(0.1f, 0.62f, 0.22f, 0.08f),
        Ring(0.48f, 0.58f, 0.09f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    legLever("legLever", "Leva per gambe", listOf(
        SolidRect(0.2f, 0.5f, 0.3f, 0.08f),
        SolidRect(0.16f, 0.24f, 0.08f, 0.26f),
        Line(0.5f, 0.58f, 0.78f, 0.42f),
        Dot(0.83f, 0.39f, 0.075f),
        Line(0.34f, 0.58f, 0.34f, 0.88f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    squatRack("squatRack", "Rack", listOf(
        Bar(0.12f, 0.3f, 0.88f, 0.3f),
        Dot(0.17f, 0.3f, 0.09f),
        Dot(0.83f, 0.3f, 0.09f),
        Line(0.22f, 0.14f, 0.22f, 0.88f),
        Line(0.78f, 0.14f, 0.78f, 0.88f),
        Line(0.22f, 0.38f, 0.3f, 0.33f),
        Line(0.78f, 0.38f, 0.7f, 0.33f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    smithMachine("smithMachine", "Multipower", listOf(
        Line(0.26f, 0.1f, 0.26f, 0.88f),
        Line(0.74f, 0.1f, 0.74f, 0.88f),
        SolidRect(0.22f, 0.4f, 0.08f, 0.09f),
        SolidRect(0.7f, 0.4f, 0.08f, 0.09f),
        Bar(0.1f, 0.445f, 0.9f, 0.445f),
        Dot(0.15f, 0.445f, 0.08f),
        Dot(0.85f, 0.445f, 0.08f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    hyperBench("hyperBench", "Panca iperestensioni", listOf(
        Bar(0.24f, 0.68f, 0.62f, 0.32f),
        Line(0.44f, 0.52f, 0.44f, 0.86f),
        Dot(0.72f, 0.68f, 0.06f),
        Dot(0.82f, 0.68f, 0.06f),
        Line(0.66f, 0.68f, 0.66f, 0.86f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    calfPlatform("calfPlatform", "Pedana calf", listOf(
        SolidRect(0.74f, 0.36f, 0.12f, 0.34f),
        Line(0.74f, 0.445f, 0.86f, 0.445f),
        Line(0.74f, 0.53f, 0.86f, 0.53f),
        Line(0.74f, 0.615f, 0.86f, 0.615f),
        SolidRect(0.18f, 0.68f, 0.5f, 0.09f),
        Line(0.28f, 0.18f, 0.28f, 0.68f),
        SolidRect(0.22f, 0.22f, 0.3f, 0.08f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    abBench("abBench", "Panca addominali", listOf(
        Bar(0.2f, 0.72f, 0.68f, 0.42f),
        Line(0.28f, 0.7f, 0.28f, 0.86f),
        Line(0.62f, 0.46f, 0.62f, 0.86f),
        Dot(0.76f, 0.38f, 0.06f),
        Dot(0.76f, 0.52f, 0.06f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    romanChair("romanChair", "Sedia romana", listOf(
        Line(0.28f, 0.12f, 0.28f, 0.88f),
        SolidRect(0.28f, 0.24f, 0.07f, 0.26f),
        SolidRect(0.34f, 0.34f, 0.26f, 0.07f),
        Dot(0.5f, 0.2f, 0.075f),
        Line(0.5f, 0.44f, 0.5f, 0.62f),
        Line(0.5f, 0.62f, 0.68f, 0.6f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    bodyweight("bodyweight", "Corpo libero", listOf(
        Dot(0.5f, 0.22f, 0.095f),
        Line(0.5f, 0.32f, 0.5f, 0.58f),
        Line(0.28f, 0.42f, 0.72f, 0.42f),
        Line(0.5f, 0.58f, 0.34f, 0.8f),
        Line(0.5f, 0.58f, 0.66f, 0.8f)
    )),
    machine("machine", "Macchina generica", listOf(
        SolidRect(0.1f, 0.26f, 0.16f, 0.56f),
        Line(0.1f, 0.4f, 0.26f, 0.4f),
        Line(0.1f, 0.54f, 0.26f, 0.54f),
        Line(0.1f, 0.68f, 0.26f, 0.68f),
        Line(0.34f, 0.14f, 0.34f, 0.88f),
        SolidRect(0.42f, 0.56f, 0.28f, 0.08f),
        SolidRect(0.42f, 0.3f, 0.07f, 0.26f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    legCurlLying("legCurlLying", "Leg curl sdraiato", listOf(
        SolidRect(0.1f, 0.46f, 0.56f, 0.08f),
        Line(0.2f, 0.54f, 0.2f, 0.88f),
        Line(0.56f, 0.54f, 0.56f, 0.88f),
        Line(0.66f, 0.5f, 0.82f, 0.3f),
        Dot(0.84f, 0.27f, 0.07f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    legCurlSeated("legCurlSeated", "Leg curl seduto", listOf(
        SolidRect(0.16f, 0.24f, 0.08f, 0.3f),
        SolidRect(0.2f, 0.54f, 0.3f, 0.08f),
        SolidRect(0.3f, 0.4f, 0.2f, 0.07f),
        Line(0.5f, 0.58f, 0.8f, 0.5f),
        Dot(0.82f, 0.58f, 0.07f),
        Line(0.34f, 0.62f, 0.34f, 0.88f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    hackSquat("hackSquat", "Hack squat", listOf(
        Line(0.16f, 0.88f, 0.58f, 0.12f),
        Bar(0.38f, 0.66f, 0.58f, 0.32f),
        Dot(0.64f, 0.3f, 0.06f),
        Bar(0.36f, 0.8f, 0.68f, 0.74f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    squatMachine("squatMachine", "Squat a leva", listOf(
        Line(0.18f, 0.36f, 0.18f, 0.88f),
        Bar(0.18f, 0.36f, 0.7f, 0.28f),
        Dot(0.46f, 0.39f, 0.055f),
        Ring(0.78f, 0.27f, 0.11f),
        SolidRect(0.3f, 0.8f, 0.42f, 0.07f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    hipThrust("hipThrust", "Hip thrust", listOf(
        SolidRect(0.06f, 0.42f, 0.26f, 0.1f),
        Line(0.12f, 0.52f, 0.12f, 0.88f),
        Line(0.26f, 0.52f, 0.26f, 0.88f),
        Ring(0.62f, 0.64f, 0.22f),
        Dot(0.62f, 0.64f, 0.06f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    gluteMachine("gluteMachine", "Glute machine", listOf(
        SolidRect(0.06f, 0.3f, 0.12f, 0.5f),
        Line(0.06f, 0.425f, 0.18f, 0.425f),
        Line(0.06f, 0.55f, 0.18f, 0.55f),
        Line(0.06f, 0.675f, 0.18f, 0.675f),
        Line(0.26f, 0.14f, 0.26f, 0.88f),
        SolidRect(0.32f, 0.3f, 0.22f, 0.07f),
        Line(0.42f, 0.58f, 0.78f, 0.42f),
        Bar(0.72f, 0.32f, 0.86f, 0.54f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    hipAdductor("hipAdductor", "Adduttori / abduttori", listOf(
        SolidRect(0.4f, 0.16f, 0.2f, 0.32f),
        SolidRect(0.28f, 0.5f, 0.44f, 0.08f),
        Bar(0.42f, 0.6f, 0.18f, 0.78f),
        Bar(0.58f, 0.6f, 0.82f, 0.78f),
        Line(0.5f, 0.58f, 0.5f, 0.88f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    calfSeated("calfSeated", "Calf seduto", listOf(
        SolidRect(0.1f, 0.56f, 0.28f, 0.08f),
        Line(0.24f, 0.64f, 0.24f, 0.88f),
        SolidRect(0.36f, 0.42f, 0.22f, 0.07f),
        Line(0.47f, 0.44f, 0.76f, 0.38f),
        Ring(0.82f, 0.37f, 0.09f),
        SolidRect(0.5f, 0.78f, 0.16f, 0.07f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    ghd("ghd", "Glute ham", listOf(
        Line(0.1f, 0.74f, 0.88f, 0.74f),
        Line(0.18f, 0.74f, 0.18f, 0.88f),
        Line(0.8f, 0.74f, 0.8f, 0.88f),
        Bar(0.2f, 0.36f, 0.2f, 0.6f),
        Line(0.2f, 0.6f, 0.2f, 0.74f),
        Dot(0.29f, 0.42f, 0.05f),
        Dot(0.29f, 0.56f, 0.05f),
        SolidRect(0.48f, 0.46f, 0.32f, 0.16f),
        Line(0.64f, 0.62f, 0.64f, 0.74f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    kettlebell("kettlebell", "Kettlebell", listOf(
        Ring(0.5f, 0.3f, 0.15f),
        Dot(0.5f, 0.6f, 0.25f)
    )),
    trapBar("trapBar", "Trap bar", listOf(
        Bar(0.3f, 0.5f, 0.4f, 0.32f),
        Bar(0.4f, 0.32f, 0.6f, 0.32f),
        Bar(0.6f, 0.32f, 0.7f, 0.5f),
        Bar(0.7f, 0.5f, 0.6f, 0.68f),
        Bar(0.6f, 0.68f, 0.4f, 0.68f),
        Bar(0.4f, 0.68f, 0.3f, 0.5f),
        Bar(0.06f, 0.5f, 0.3f, 0.5f),
        Bar(0.7f, 0.5f, 0.94f, 0.5f),
        SolidRect(0.12f, 0.32f, 0.08f, 0.36f),
        SolidRect(0.8f, 0.32f, 0.08f, 0.36f),
        Line(0.42f, 0.44f, 0.42f, 0.56f),
        Line(0.58f, 0.44f, 0.58f, 0.56f)
    )),
    ezBar("ezBar", "Bilanciere EZ", listOf(
        Bar(0.06f, 0.5f, 0.3f, 0.5f),
        Bar(0.3f, 0.5f, 0.37f, 0.4f),
        Bar(0.37f, 0.4f, 0.44f, 0.58f),
        Bar(0.44f, 0.58f, 0.5f, 0.48f),
        Bar(0.5f, 0.48f, 0.56f, 0.58f),
        Bar(0.56f, 0.58f, 0.63f, 0.4f),
        Bar(0.63f, 0.4f, 0.7f, 0.5f),
        Bar(0.7f, 0.5f, 0.94f, 0.5f),
        SolidRect(0.1f, 0.32f, 0.08f, 0.36f),
        SolidRect(0.82f, 0.32f, 0.08f, 0.36f)
    )),
    landmine("landmine", "Landmine", listOf(
        SolidRect(0.06f, 0.8f, 0.16f, 0.08f),
        Bar(0.14f, 0.82f, 0.74f, 0.3f),
        Dot(0.78f, 0.27f, 0.13f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    preacherBench("preacherBench", "Panca Scott", listOf(
        SolidRect(0.1f, 0.58f, 0.24f, 0.08f),
        Line(0.22f, 0.66f, 0.22f, 0.88f),
        Bar(0.4f, 0.32f, 0.66f, 0.54f),
        Line(0.52f, 0.44f, 0.52f, 0.88f),
        Dot(0.75f, 0.6f, 0.06f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    dipStation("dipStation", "Parallele", listOf(
        Line(0.24f, 0.32f, 0.24f, 0.88f),
        Line(0.76f, 0.32f, 0.76f, 0.88f),
        Bar(0.12f, 0.32f, 0.38f, 0.32f),
        Bar(0.62f, 0.32f, 0.88f, 0.32f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    assistMachine("assistMachine", "Trazioni assistite", listOf(
        SolidRect(0.06f, 0.34f, 0.12f, 0.46f),
        Line(0.06f, 0.455f, 0.18f, 0.455f),
        Line(0.06f, 0.57f, 0.18f, 0.57f),
        Line(0.06f, 0.685f, 0.18f, 0.685f),
        Line(0.26f, 0.1f, 0.26f, 0.88f),
        Line(0.26f, 0.12f, 0.76f, 0.12f),
        Dot(0.76f, 0.12f, 0.05f),
        Bar(0.5f, 0.4f, 0.78f, 0.4f),
        SolidRect(0.44f, 0.62f, 0.28f, 0.07f),
        Line(0.26f, 0.66f, 0.44f, 0.66f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    rowMachine("rowMachine", "Rematore machine", listOf(
        SolidRect(0.06f, 0.3f, 0.12f, 0.5f),
        Line(0.06f, 0.425f, 0.18f, 0.425f),
        Line(0.06f, 0.55f, 0.18f, 0.55f),
        Line(0.06f, 0.675f, 0.18f, 0.675f),
        Line(0.24f, 0.16f, 0.24f, 0.88f),
        Line(0.24f, 0.4f, 0.4f, 0.4f),
        Dot(0.43f, 0.4f, 0.055f),
        SolidRect(0.54f, 0.28f, 0.07f, 0.3f),
        SolidRect(0.54f, 0.62f, 0.3f, 0.07f),
        Line(0.68f, 0.69f, 0.68f, 0.88f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    cableRope("cableRope", "Cavo con corda", listOf(
        SolidRect(0.1f, 0.12f, 0.1f, 0.76f),
        Line(0.15f, 0.14f, 0.6f, 0.14f),
        Ring(0.6f, 0.2f, 0.05f),
        Line(0.6f, 0.25f, 0.6f, 0.46f),
        Line(0.6f, 0.46f, 0.5f, 0.68f),
        Line(0.6f, 0.46f, 0.7f, 0.68f),
        Dot(0.49f, 0.71f, 0.05f),
        Dot(0.71f, 0.71f, 0.05f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    cablePushdown("cablePushdown", "Cavo alto con barra", listOf(
        SolidRect(0.1f, 0.12f, 0.1f, 0.76f),
        Line(0.15f, 0.14f, 0.6f, 0.14f),
        Ring(0.6f, 0.2f, 0.05f),
        Line(0.6f, 0.25f, 0.6f, 0.52f),
        Bar(0.44f, 0.54f, 0.76f, 0.54f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    sled("sled", "Slitta", listOf(
        Bar(0.1f, 0.82f, 0.74f, 0.82f),
        Line(0.74f, 0.82f, 0.88f, 0.7f),
        Bar(0.24f, 0.8f, 0.14f, 0.2f),
        Line(0.58f, 0.82f, 0.58f, 0.34f),
        SolidRect(0.4f, 0.6f, 0.36f, 0.09f),
        SolidRect(0.4f, 0.46f, 0.36f, 0.09f)
    )),
    dumbbellBench("dumbbellBench", "Panca e manubri", listOf(
        SolidRect(0.2f, 0.58f, 0.6f, 0.09f),
        Line(0.3f, 0.67f, 0.3f, 0.88f),
        Line(0.7f, 0.67f, 0.7f, 0.88f),
        Bar(0.4f, 0.32f, 0.6f, 0.32f),
        SolidRect(0.29f, 0.21f, 0.1f, 0.22f),
        SolidRect(0.61f, 0.21f, 0.1f, 0.22f)
    )),
    inclineDumbbell("inclineDumbbell", "Panca inclinata e manubri", listOf(
        Bar(0.24f, 0.76f, 0.6f, 0.48f),
        Line(0.28f, 0.76f, 0.28f, 0.88f),
        Line(0.56f, 0.52f, 0.56f, 0.88f),
        SolidRect(0.16f, 0.74f, 0.16f, 0.07f),
        Bar(0.62f, 0.22f, 0.8f, 0.22f),
        SolidRect(0.53f, 0.12f, 0.09f, 0.2f),
        SolidRect(0.8f, 0.12f, 0.09f, 0.2f)
    )),
    abMachine("abMachine", "Macchina addominali", listOf(
        SolidRect(0.06f, 0.3f, 0.12f, 0.5f),
        Line(0.06f, 0.425f, 0.18f, 0.425f),
        Line(0.06f, 0.55f, 0.18f, 0.55f),
        Line(0.06f, 0.675f, 0.18f, 0.675f),
        Line(0.26f, 0.14f, 0.26f, 0.88f),
        SolidRect(0.36f, 0.62f, 0.26f, 0.07f),
        SolidRect(0.36f, 0.32f, 0.07f, 0.3f),
        Line(0.4f, 0.22f, 0.7f, 0.3f),
        Line(0.7f, 0.3f, 0.7f, 0.44f),
        Dot(0.7f, 0.47f, 0.06f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    deltMachine("deltMachine", "Alzate laterali machine", listOf(
        SolidRect(0.42f, 0.2f, 0.16f, 0.34f),
        SolidRect(0.32f, 0.56f, 0.36f, 0.08f),
        Line(0.5f, 0.64f, 0.5f, 0.88f),
        Bar(0.4f, 0.36f, 0.14f, 0.26f),
        Bar(0.6f, 0.36f, 0.86f, 0.26f),
        Dot(0.12f, 0.25f, 0.06f),
        Dot(0.88f, 0.25f, 0.06f),
        Line(0.08f, 0.88f, 0.92f, 0.88f)
    )),
    floorMat("floorMat", "A terra", listOf(
        SolidRect(0.06f, 0.78f, 0.88f, 0.07f),
        Dot(0.2f, 0.44f, 0.075f),
        Line(0.28f, 0.48f, 0.84f, 0.7f),
        Line(0.3f, 0.52f, 0.3f, 0.76f)
    )),
    plate("plate", "Disco", listOf(
        Ring(0.5f, 0.5f, 0.36f),
        Ring(0.5f, 0.5f, 0.2f),
        Dot(0.5f, 0.5f, 0.07f)
    ));

    companion object {
        fun of(raw: String?): MachineGlyph? = entries.firstOrNull { it.raw == raw }
    }
}
