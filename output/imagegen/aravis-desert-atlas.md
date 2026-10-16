# Aravis desert terrain

Generated with the built-in image_gen tool. The source atlas is `aravis-desert-atlas.png`; runtime sprites are in `sprites/blocks/environment/aravis/`. Each atlas cell is cropped and resized to 32×32 using nearest-neighbor sampling. Existing `aeolian-sand1.png` and `red-sand1.png` are preserved.

Prompt: Create a square 4×4 terrain atlas for Mindustry desert planet Aravis, with no gaps, borders, labels or transparency. Low-resolution top-down pixel art, simple flat pixel clusters, no perspective shadows. Row 1: four variants of pale grey beige aeolian sand with wind ripples, base #9c9284. Row 2: four variants of terracotta red sand with ochre wind ripples, base #ba6947. Row 3: four variants of ochre gravelly desert sand with angular dark pebbles, base #a8855c. Row 4: four variants of dry cracked desert clay with irregular polygon cracks, base #8d7056. Soft muted tile edges; no objects, plants, structures, text or watermarks.

New floors are available in the map editor. Existing maps gain additional aeolian/red sand texture variants without changing tile resources, buildings or routes.
