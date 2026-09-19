const test = require('node:test');
const assert = require('node:assert/strict');
const SavedPlaces = require('../../main/resources/static/js/reservation/saved-places.js');
const dates = require('../../main/resources/static/js/reservation/stay-dates.js');

test('stay duration works across month/year/leap-day boundaries', () => {
    assert.equal(dates.nights('2026-09-30', '2026-10-02'), 2);
    assert.equal(dates.nights('2026-12-31', '2027-01-02'), 2);
    assert.equal(dates.nights('2028-02-28', '2028-03-01'), 2);
    assert.equal(dates.nights('2026-09-19', '2026-09-19'), 0);
    assert.equal(dates.nights('', '2026-09-19'), 0);
});
test('save, unsave with empty 204 response, and save again persist through API', async () => {
    const calls = [];
    const store = new SavedPlaces(async (url, options) => {
        calls.push({url, ...options});
        return options.method ? {ok: true} : {ok: true, json: async () => []};
    }, {'X-CSRF-TOKEN': 'token'});
    const place = {contentId: '123', contentTypeId: '32'};
    await store.toggle(place);
    assert.ok(store.ids.has('123'));
    await store.toggle(place);
    assert.ok(!store.ids.has('123'));
    await store.toggle(place);
    assert.ok(store.ids.has('123'));
    assert.deepEqual(calls.slice(1).map(x => x.method), ['POST', 'DELETE', 'POST']);
    assert.equal(calls[2].url, '/tour/api/saved-places/123');
    assert.equal(calls[2].headers['X-CSRF-TOKEN'], 'token');
});
test('failed unsave retains state and allows retry', async () => {
    let ok = false;
    const store = new SavedPlaces(async (_, options) => options.method ? {ok} : {ok: true, json: async () => ['123']});
    await assert.rejects(store.toggle({contentId: '123'}));
    assert.ok(store.ids.has('123'));
    assert.equal(store.pending.size, 0);
    ok = true;
    await store.toggle({contentId: '123'});
    assert.ok(!store.ids.has('123'));
});
test('double click sends only one mutation while request is pending', async () => {
    let release;
    let writes = 0;
    const store = new SavedPlaces(async (_, options) => {
        if (!options.method) return {ok: true, json: async () => []};
        writes++;
        await new Promise(resolve => { release = resolve; });
        return {ok: true};
    });
    await store.load();
    const first = store.toggle({contentId: '123'});
    await store.toggle({contentId: '123'});
    release();
    await first;
    assert.equal(writes, 1);
});
