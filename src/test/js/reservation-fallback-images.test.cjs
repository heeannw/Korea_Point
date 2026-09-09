const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '../../main/resources/static');
function session(storage = new Map()) {
    const context = vm.createContext({window: {}, localStorage: {
        getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value)
    }});
    for (const name of ['fallback-catalog.js', 'fallback-images.js']) {
        vm.runInContext(fs.readFileSync(path.join(root, 'js/reservation', name), 'utf8'), context);
    }
    return {catalog: context.window.RESERVATION_FALLBACK_CATALOG, get: context.window.ReservationFallbackImages.get};
}
const types = {'tourist-attraction': 12, culture: 14, festival: 15, 'travel-course': 25,
    leports: 28, lodging: 32, shopping: 38, food: 39};

test('every catalog place has its own scene, stable across order, reloads and language aliases', () => {
    const first = session(), second = session();
    for (const [category, ids] of Object.entries(first.catalog)) {
        const keys = new Set();
        for (const contentId of [...ids].reverse()) {
            const place = {contentId, contentTypeId: types[category]};
            const scene = first.get(place);
            assert.ok(scene);
            assert.ok(!keys.has(scene.key), category + ': repeated scene');
            keys.add(scene.key);
            assert.equal(scene.key, second.get({...place, title: 'Translated title'}).key);
        }
    }
    for (const [type, alias] of [[12,76],[14,78],[15,85],[25,77],[28,75],[32,80],[38,79],[39,82]]) {
        const place = {contentId: 'alias-test', contentTypeId: type};
        assert.equal(first.get(place).key, first.get({...place,contentTypeId:alias}).key);
    }
});

test('new places use free scenes, persist safely and never recycle occupied scenes on exhaustion', () => {
    const storage = new Map(), first = session(storage);
    const seen = new Set(first.catalog.food.map(contentId => first.get({contentId,contentTypeId:39}).key));
    const available = 82 - seen.size;
    for (let i = 0; i < available; i++) {
        const place = {contentId: 'new-' + i, contentTypeId: 39};
        const scene = first.get(place);
        assert.ok(scene);
        assert.ok(!seen.has(scene.key));
        seen.add(scene.key);
        assert.equal(session(storage).get(place).key, scene.key);
    }
    assert.equal(first.get({contentId:'over-capacity',contentTypeId:39}), null);
    assert.equal(session(storage).get({contentId:'over-capacity',contentTypeId:39}), null);
});

test('invalid or unavailable browser storage cannot break catalog assignments', () => {
    const storage = new Map([['reservation-fallback-assignments-v1', '{invalid']]);
    assert.ok(session(storage).get({contentId:'new',contentTypeId:39}));
    const denied = {get: () => {throw new Error('denied');},set: () => {throw new Error('denied');}};
    assert.ok(session(denied).get({contentId:'new',contentTypeId:39}));
});

test('every catalog scene references an existing project asset', () => {
    const current = session();
    for (const [category, ids] of Object.entries(current.catalog)) {
        for (const contentId of ids) {
            const scene = current.get({contentId,contentTypeId:types[category]});
            assert.ok(fs.existsSync(path.join(root,scene.url)), scene.url);
        }
    }
});
