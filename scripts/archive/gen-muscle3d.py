# ARCHIVIATO (28 ottobre 2026): il manichino 3D non è piaciuto e non è nell'app. Resta qui come riferimento.

# Genera il manichino 3D dei muscoli da Z-Anatomy (CC BY-SA 4.0, da BodyParts3D CC BY-SA 2.1 JP).
# Un oggetto per muscolo dell'app (nome = id di common/muscle-info.ts), più "other" (gli altri muscoli superficiali)
# e "bones" (lo scheletro), semplificati per il telefono. Esce in glb (web) e usdz (iPhone).
#
#   Blender -b Z-Anatomy/Startup.blend --python scripts/gen-muscle3d.py -- <cartella di uscita>
import re, sys, bpy, bmesh

OUT = sys.argv[sys.argv.index('--') + 1]

MAP = {
    'chest': r'pectoralis major',
    'deltoids': r'part of deltoid muscle',
    'trapezius': r'part of trapezius muscle',
    'upperBack': r'latissimus dorsi|rhomboid m|teres (major|minor) muscle|infraspinatus muscle',
    'lowerBack': r'iliocostalis (lumborum|thoracis)|longissimus thoracis|spinalis thoracis',
    'abs': r'rectus abdominis|pyramidalis',
    'obliques': r'external abdominal oblique|serratus anterior',
    'biceps': r'head of biceps brachii|brachialis muscle',
    'triceps': r'head of triceps brachii|anconeus',
    'forearm': r'brachioradialis|carpi|extensor digitorum\.|extensor digiti minimi|palmaris longus|pronator teres|flexor digitorum superficialis|abductor pollicis longus|extensor pollicis',
    'quadriceps': r'rectus femoris|vastus',
    'adductors': r'adductor (longus|magnus|brevis)|gracilis|pectineus',
    'tibialis': r'tibialis anterior',
    'gluteal': r'gluteus (maximus|medius) muscle|tensor fasciae latae',
    'hamstring': r'biceps femoris\.|semitendinosus muscle|semimembranosus muscle',
    'calves': r'head of gastrocnemius|soleus muscle',
}
# Non muscoli o muscoli che non si vedono da fuori: borse, fasce, guaine, occhio, gola, pavimento pelvico, intercostali.
SKIP = re.compile(r'bursa|fascia|sheath|retinacul|septum|ligament|aponeuros|tendinous|tarsus|intercostal|pharyn|crico|arytenoid|thyro|'
                  r'glossus|palat|stylo|pterygoid|(superior|inferior|lateral|medial) rectus muscle|(superior|inferior) oblique muscle|'
                  r'levator palpebrae|coccygeus|pubo|levator ani|anal sphincter|obturator|gemellus|rotatores|interspinales|intertransvers|'
                  r'levatores|longus (colli|capitis)|rectus .*capitis|obliquus .*capitis|multifidus|psoas|iliacus|transversus|'
                  r'internal abdominal oblique|subscapularis|serratus posterior|tibialis posterior|popliteus|quadratus', re.I)
# Triangoli circa per ogni oggetto finale: i muscoli colorati più fini, il resto grezzo.
BUDGET = {'other': 40000, 'bones': 25000}
MUSCLE_BUDGET = 5000

deps = bpy.context.evaluated_depsgraph_get()

def meshes(collection):
    seen = set()
    def walk(c):
        for o in c.objects:
            # ".g" sono le scritte dei titoli («SKELETAL SYSTEM»), non anatomia.
            if o.type == 'MESH' and o.name not in seen and not o.name.endswith('.g'):
                seen.add(o.name)
                yield o
        for ch in c.children:
            yield from walk(ch)
    return list(walk(collection))

def merged(name, objs):
    """Un oggetto solo con le mesh valutate (modificatori applicati) in coordinate mondo."""
    bm = bmesh.new()
    for o in objs:
        ev = o.evaluated_get(deps)
        me = ev.to_mesh()
        if len(me.polygons):
            me.transform(o.matrix_world)
            bm.from_mesh(me)
        ev.to_mesh_clear()
    bmesh.ops.triangulate(bm, faces=bm.faces[:])
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    out.objects.link(ob)
    return ob

def decimate(ob, target):
    tris = len(ob.data.polygons)
    if tris > target:
        mod = ob.modifiers.new('d', 'DECIMATE')
        mod.ratio = target / tris
        bpy.context.view_layer.objects.active = ob
        bpy.ops.object.modifier_apply(modifier='d')
    for p in ob.data.polygons:
        p.use_smooth = True
    print(ob.name, tris, '->', len(ob.data.polygons))

out = bpy.data.collections.new('peak')
bpy.context.scene.collection.children.link(out)

muscles = [o for o in meshes(bpy.data.collections['4: Muscular system']) if not SKIP.search(o.name)]
groups = {k: [] for k in MAP}
other = []
for o in muscles:
    key = next((k for k, rx in MAP.items() if re.search(rx, o.name, re.I)), None)
    (groups[key] if key else other).append(o)
for k, objs in groups.items():
    print('#', k, sorted({re.sub(r'\.[lrj]$', '', o.name) for o in objs}))

made = [merged(k, objs) for k, objs in groups.items()]
made.append(merged('other', other))
made.append(merged('bones', meshes(bpy.data.collections['1: Skeletal system'])))
for ob in made:
    decimate(ob, BUDGET.get(ob.name, MUSCLE_BUDGET))

# Solo i nostri oggetti, centrati a terra.
for c in list(bpy.context.scene.collection.children):
    if c != out:
        bpy.context.scene.collection.children.unlink(c)
bpy.ops.object.select_all(action='DESELECT')
for ob in made:
    ob.select_set(True)
bpy.ops.export_scene.gltf(filepath=f'{OUT}/muscles.glb', use_selection=True, export_apply=True, export_normals=True,
                          export_materials='NONE', export_draco_mesh_compression_enable=False)
bpy.ops.wm.usd_export(filepath=f'{OUT}/muscles.usdz', selected_objects_only=True, export_materials=False,
                      export_uvmaps=False, convert_orientation=True, export_global_up_selection='Y', export_global_forward_selection='NEGATIVE_Z')
