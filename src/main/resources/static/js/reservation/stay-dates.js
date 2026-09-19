(function (root) {
    const api = {
        nights(start, end) {
            if (!/^\d{4}-\d{2}-\d{2}$/.test(start) || !/^\d{4}-\d{2}-\d{2}$/.test(end)) return 0;
            return Math.round((Date.parse(end + 'T00:00:00Z') - Date.parse(start + 'T00:00:00Z')) / 86400000);
        }
    };
    root.ReservationStayDates = api;
    if (typeof module !== 'undefined') module.exports = api;
})(globalThis);
