import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useApi } from '../api/useApi';
import { CAR_TYPES } from '../api/catalog';
import CarArt from '../components/CarArt';
import { Message, Empty, Skeleton } from '../components/ui';
import { fmtMoney, todayISO, dateRangeError, rentalDays } from '../components/format';

const SORTS = {
  'price-asc': (a, b) => Number(a.dailyRate) - Number(b.dailyRate),
  'price-desc': (a, b) => Number(b.dailyRate) - Number(a.dailyRate),
  'year-desc': (a, b) => (b.year || 0) - (a.year || 0),
};

function sortCars(cars, sort) {
  return [...cars].sort(SORTS[sort] || SORTS['price-asc']);
}

/** "2026-12-10" -> "10 Dec" */
function fmtShort(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

export default function BrowsePage() {
  const api = useApi();
  const navigate = useNavigate();
  // The search lives in the URL so Back from My bookings returns to the same
  // results, and a search can be shared as a link.
  const [params, setParams] = useSearchParams();
  const [location, setLocation] = useState(params.get('location') || '');
  const [type, setType] = useState(params.get('type') || '');
  const [startDate, setStartDate] = useState(params.get('startDate') || '');
  const [endDate, setEndDate] = useState(params.get('endDate') || '');
  const [locations, setLocations] = useState([]);
  const [sort, setSort] = useState('price-asc');
  // The search that results are shown for; null until the first search. Seeded
  // from the URL so Back and shared links restore it.
  const [query, setQuery] = useState(() => {
    if (![...params.keys()].length) return null;
    const q = { location: params.get('location') || '', type: params.get('type') || '', startDate: params.get('startDate') || '', endDate: params.get('endDate') || '' };
    return q.startDate || q.endDate ? (dateRangeError(q.startDate, q.endDate) ? null : q) : q;
  });
  const [cars, setCars] = useState(null);
  const [err, setErr] = useState(() => {
    const s0 = params.get('startDate'), e0 = params.get('endDate');
    return s0 || e0 ? dateRangeError(s0, e0) : '';
  });
  const [busy, setBusy] = useState(() => query !== null);
  const [bookingDraft, setBookingDraft] = useState(null); // {carId, location, startDate, endDate}
  const [bookErr, setBookErr] = useState('');
  const [booking, setBooking] = useState(false);
  const bookingPanel = useRef(null);
  const today = todayISO();

  // The booking form renders below the results grid. Without this, clicking
  // "Book this car" on a long list changed nothing the user could see.
  const draftCarId = bookingDraft?.carId;
  useEffect(() => {
    if (draftCarId == null || !bookingPanel.current) return;
    bookingPanel.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    bookingPanel.current.querySelector('input:not([readonly])')?.focus({ preventScroll: true });
  }, [draftCarId]);

  useEffect(() => {
    // Known pickup locations for the Location suggestions; best effort.
    api.catalog.search('', '')
      .then((all) => setLocations([...new Set(all.map((c) => c.location).filter(Boolean))].sort()))
      .catch(() => {});
  }, [api]);

  useEffect(() => {
    if (!query) return undefined;
    let active = true;
    const { location: loc, type: t, startDate: sd, endDate: ed } = query;
    (sd && ed
      ? api.reservations.availability({ location: loc, type: t, startDate: sd, endDate: ed })
      : api.catalog.search(loc, t))
      .then((results) => {
        if (!active) return;
        setErr('');
        setCars(results);
      })
      .catch((e) => {
        if (!active) return;
        setErr(e.message);
        setCars(null);
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => {
      active = false;
    };
  }, [api, query]);

  function clearFilters() {
    setLocation(''); setType(''); setStartDate(''); setEndDate('');
    setQuery(null); setCars(null); setErr(''); setBusy(false); setBookingDraft(null);
    setParams({}, { replace: true });
  }

  function runSearch(e) {
    e.preventDefault();
    if (startDate || endDate) {
      const rangeErr = dateRangeError(startDate, endDate);
      if (rangeErr) {
        setErr(rangeErr);
        return;
      }
    }
    const values = { location, type, startDate, endDate };
    setParams(Object.fromEntries(Object.entries(values).filter(([, v]) => v)), { replace: true });
    setErr('');
    setBusy(true);
    // A fresh object every time, so pressing Search again refetches.
    setQuery({ ...values });
  }

  function openBooking(car) {
    const carId = car.carId ?? car.id;
    setBookErr('');
    setBookingDraft({ carId, car, pickupLocation: car.location || '', startDate, endDate });
  }

  async function submitBooking(e) {
    e.preventDefault();
    setBookErr('');
    const rangeErr = dateRangeError(bookingDraft.startDate, bookingDraft.endDate);
    if (rangeErr) {
      setBookErr(rangeErr);
      return;
    }
    setBooking(true);
    try {
      const booking = await api.reservations.create({
        carId: Number(bookingDraft.carId),
        startDate: bookingDraft.startDate,
        endDate: bookingDraft.endDate,
        pickupLocation: bookingDraft.pickupLocation,
      });
      setBookingDraft(null);
      navigate('/bookings', { state: { justCreated: booking.id } });
    } catch (e2) {
      setBookErr(e2.message);
    } finally {
      setBooking(false);
    }
  }

  return (
    <>
      <div className="panel">
        <h2>Check availability</h2>
        <div className="hint" style={{ marginBottom: 12 }}>
          Search by dates to see cars free for that window, or leave dates blank to browse the whole fleet.
        </div>
        <form className="inline-form" onSubmit={runSearch}>
          <div className="field">
            <label>Location</label>
            <input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Any location" list="rentez-locations" />
            <datalist id="rentez-locations">
              {locations.map((l) => <option key={l} value={l} />)}
            </datalist>
          </div>
          <div className="field">
            <label>Type</label>
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">Any</option>
              {CAR_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div className="field">
            <label>Start date</label>
            <input type="date" value={startDate} min={today} onChange={(e) => setStartDate(e.target.value)} />
          </div>
          <div className="field">
            <label>End date</label>
            <input type="date" value={endDate} min={startDate || today} onChange={(e) => setEndDate(e.target.value)} />
          </div>
          <div className="field">
            <div className="row" style={{ gap: 6 }}>
              <button type="submit" className="primary" disabled={busy}>{busy ? 'Searching…' : 'Search'}</button>
              {(location || type || startDate || endDate || cars) && (
                <button type="button" onClick={clearFilters}>Clear</button>
              )}
            </div>
          </div>
        </form>
        <Message text={err} kind="err" />
        {busy && cars === null ? (
          <Skeleton rows={3} card />
        ) : cars === null ? (
          <Empty>Pick a location or dates and press Search — or just press Search to see every car.</Empty>
        ) : cars.length === 0 ? (
          <Empty>
            No cars match {startDate && endDate ? `${fmtShort(startDate)}–${fmtShort(endDate)}` : 'that search'}. Try other dates, another location or any type.
          </Empty>
        ) : (
          <>
            <div className="results-bar">
              <div className="hint">
                <strong>{cars.length}</strong> {cars.length === 1 ? 'car' : 'cars'} available
                {startDate && endDate ? ` ${fmtShort(startDate)} – ${fmtShort(endDate)}` : ''}
                {location ? ` in ${location}` : ''}
              </div>
              <label className="hint" style={{ display: 'flex', alignItems: 'center', gap: 6, margin: 0 }}>
                Sort by
                <select value={sort} onChange={(e) => setSort(e.target.value)}>
                  <option value="price-asc">Price: low to high</option>
                  <option value="price-desc">Price: high to low</option>
                  <option value="year-desc">Newest first</option>
                </select>
              </label>
            </div>
            <div className="cardgrid">
              {sortCars(cars, sort).map((c) => {
                const id = c.carId ?? c.id;
                return (
                  <div className="carcard" key={id}>
                    <CarArt type={c.type} />
                    <span className="type mono">{c.type}</span>
                    <div className="body">
                      <h4>{c.make} {c.model}</h4>
                      <div className="chips">
                        {c.year && <span className="chip">{c.year}</span>}
                        {c.location && <span className="chip">{c.location}</span>}
                      </div>
                      <div className="foot">
                        <div className="rate">{fmtMoney(c.dailyRate)}<span> /day</span></div>
                        <button className="accent small" onClick={() => openBooking(c)}
                          aria-label={`Book the ${c.make} ${c.model}`}>Book this car</button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </div>

      {bookingDraft && (
        <div className="panel" ref={bookingPanel}>
          <h2>New booking</h2>
          <form onSubmit={submitBooking}>
            <div className="row">
              <div className="field">
                <label>Car</label>
                <input
                  value={`${bookingDraft.car.make} ${bookingDraft.car.model}${bookingDraft.car.year ? ` (${bookingDraft.car.year})` : ''}`}
                  readOnly
                />
              </div>
              <div className="field">
                <label>Price to pay</label>
                <input
                  value={rentalDays(bookingDraft.startDate, bookingDraft.endDate) > 0
                    ? fmtMoney(rentalDays(bookingDraft.startDate, bookingDraft.endDate) * Number(bookingDraft.car.dailyRate))
                    : 'Choose your dates'}
                  readOnly
                  aria-live="polite"
                  style={{ fontWeight: 600 }}
                />
                <div className="hint" style={{ marginTop: 4 }}>
                  {rentalDays(bookingDraft.startDate, bookingDraft.endDate) > 0
                    ? `${rentalDays(bookingDraft.startDate, bookingDraft.endDate)} day(s) × ${fmtMoney(bookingDraft.car.dailyRate)}/day`
                    : `${fmtMoney(bookingDraft.car.dailyRate)}/day`}
                </div>
              </div>
              <div className="field">
                <label>Pickup location</label>
                <input value={bookingDraft.pickupLocation}
                  onChange={(e) => setBookingDraft((d) => ({ ...d, pickupLocation: e.target.value }))} />
              </div>
            </div>
            <div className="row">
              <div className="field">
                <label>Start date</label>
                <input type="date" value={bookingDraft.startDate} min={today}
                  onChange={(e) => setBookingDraft((d) => ({ ...d, startDate: e.target.value }))} required />
              </div>
              <div className="field">
                <label>End date</label>
                <input type="date" value={bookingDraft.endDate} min={bookingDraft.startDate || today}
                  onChange={(e) => setBookingDraft((d) => ({ ...d, endDate: e.target.value }))} required />
              </div>
            </div>
            <Message text={bookErr} kind="err" />
            <div className="row">
              <button type="submit" className="primary" disabled={booking}>{booking ? 'Booking…' : 'Confirm booking'}</button>
              <button type="button" onClick={() => setBookingDraft(null)}>Cancel</button>
            </div>
          </form>
        </div>
      )}
    </>
  );
}
