/* Each place owns a slot: hashing alone would assign the same scene to different places. */
(() => {
    const categories = {
        12: 'tourist-attraction', 14: 'culture', 15: 'festival', 25: 'travel-course',
        28: 'leports', 32: 'lodging', 38: 'shopping', 39: 'food',
        75: 'leports', 76: 'tourist-attraction', 77: 'travel-course', 78: 'culture',
        79: 'shopping', 80: 'lodging', 82: 'food', 85: 'festival'
    };
    const atlasCounts = {'tourist-attraction': 1, culture: 0, festival: 0,
        'travel-course': 0, leports: 1, lodging: 2, shopping: 1, food: 5};
    const assignments = new Map();
    const pools = new Map();
    const storageKey = 'reservation-fallback-assignments-v1';
    const dynamicAssignments = [];
    for (const [category, count] of Object.entries(atlasCounts)) {
        const scenes = [];
        for (let atlas = 1; atlas <= count; atlas++) {
            for (let tile = 0; tile < 16; tile++) {
                scenes.push({url: `/images/reservation/defaults/${category}-atlas-${atlas}.jpg`, tile, columns: 4, rows: 4});
            }
        }
        for (const suffix of ['', '-2']) {
            scenes.push({url: `/images/reservation/defaults/${category}${suffix}.jpg`, tile: 0, columns: 1, rows: 1});
        }
        const ids = window.RESERVATION_FALLBACK_CATALOG[category] || [];
        const used = new Set();
        ids.forEach((id, slot) => {
            if (slot >= scenes.length) throw new Error('Fallback catalog exceeds scene capacity: ' + category);
            assignments.set(`${category}:${id}`, slot);
            used.add(slot);
        });
        pools.set(category, {scenes, used});
    }
    // Validate persisted assignments against the shipped catalog, which always takes priority.
    try {
        const saved = JSON.parse(localStorage.getItem(storageKey) || '[]');
        if (Array.isArray(saved)) saved.forEach(entry => {
            if (!Array.isArray(entry)) return;
            const [category, id, slot] = entry;
            const pool = pools.get(category);
            const key = `${category}:${id}`;
            if (!pool || typeof id !== 'string' || !Number.isInteger(slot) || slot < 0 ||
                slot >= pool.scenes.length || pool.used.has(slot) || assignments.has(key)) return;
            assignments.set(key, slot);
            pool.used.add(slot);
            dynamicAssignments.push(entry);
        });
    } catch (_) { /* Storage may be disabled; session assignments still work. */ }

    function get(place) {
        const category = categories[String(place.contentTypeId)] || 'tourist-attraction';
        const id = String(place.contentId || `${place.title || ''}|${place.address || ''}`);
        const key = `${category}:${id}`;
        const pool = pools.get(category);
        if (!assignments.has(key)) {
            const slot = pool.scenes.findIndex((_, index) => !pool.used.has(index));
            // Never recycle another place's scene if a category has exhausted its supply.
            if (slot < 0) return null;
            assignments.set(key, slot);
            pool.used.add(slot);
            dynamicAssignments.push([category, id, slot]);
            try { localStorage.setItem(storageKey, JSON.stringify(dynamicAssignments)); } catch (_) {}
        }
        const scene = pool.scenes[assignments.get(key)];
        return {...scene, key: `${scene.url}#tile=${scene.tile}`};
    }
    window.ReservationFallbackImages = {get};
})();
