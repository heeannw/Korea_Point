(function (root) {
    class ReservationSavedPlaces {
        constructor(request, headers = {}) {
            this.request = request;
            this.headers = headers;
            this.ids = new Set();
            this.pending = new Set();
            this.loading = null;
        }
        load() {
            if (!this.loading) this.loading = this.request('/tour/api/saved-places/ids', {
                headers: {Accept: 'application/json'}
            }).then(async response => {
                if (!response.ok) throw Error('saved places request failed');
                this.ids = new Set((await response.json()).map(String));
            }).catch(error => { this.loading = null; throw error; });
            return this.loading;
        }
        async toggle(place) {
            const id = String(place.contentId);
            if (this.pending.has(id)) return;
            this.pending.add(id);
            try {
                await this.load();
                const saved = this.ids.has(id);
                const response = await this.request('/tour/api/saved-places' + (saved ? '/' + encodeURIComponent(id) : ''), {
                    method: saved ? 'DELETE' : 'POST',
                    headers: {...this.headers, 'Content-Type': 'application/json', Accept: 'application/json'},
                    ...(saved ? {} : {body: JSON.stringify({contentId: id, contentTypeId: place.contentTypeId})})
                });
                if (!response.ok) throw Error('saved place update failed');
                // DELETE returns 204 with no JSON body.
                if (saved) this.ids.delete(id); else this.ids.add(id);
                return !saved;
            } finally { this.pending.delete(id); }
        }
    }
    root.ReservationSavedPlaces = ReservationSavedPlaces;
    if (typeof module !== 'undefined') module.exports = ReservationSavedPlaces;
})(globalThis);
