import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useApi } from '../api/useApi';
import { CAR_TYPES } from '../api/catalog';
import { Message, Empty, fmtMoney, todayISO, dateRangeError, rentalDays } from '../components/ui';

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
  const [cars, setCars] = useState(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
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
    if ([...params.keys()].length) search();
    /* eslint-disable-next-line react-hooks/exhaustive-deps */
  }, []);

  function clearFilters() {
    setLocation(''); setType(''); setStartDate(''); setEndDate('');
    setCars(null); setErr(''); setBookingDraft(null);
    setParams({}, { replace: true });
  }

  function runSearch(e) {
    e.preventDefault();
    const next = Object.fromEntries(Object.entries({ location, type, startDate, endDate }).filter(([, v]) => v));
    setParams(next, { replace: true });
    search();
  }

  async function search() {
    setErr('');
    if (startDate || endDate) {
      const rangeErr = dateRangeError(startDate, endDate);
      if (rangeErr) {
        setErr(rangeErr);
        return;
      }
    }
    setBusy(true);
    try {
      const results = startDate && endDate
        ? await api.reservations.availability({ location, type, startDate, endDate })
        : await api.catalog.search(location, type);
      setCars(results);
    } catch (e2) {
      setErr(e2.message);
      setCars(null);
    } finally {
      setBusy(false);
    }
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
        {cars === null ? (
          <Empty>Run a search to see cars.</Empty>
        ) : cars.length === 0 ? (
          <Empty>No cars match. Try widening your search.</Empty>
        ) : (
          <div className="cardgrid">
            {cars.map((c) => {
              const id = c.carId ?? c.id;
              return (
                <div className="carcard" key={id}>
                  <span className="type mono">{c.type}</span>
                  <h4>{c.make} {c.model}</h4>
                  <div className="hint">{c.year || ''} · {c.location || ''}</div>
                  <div className="rate">{fmtMoney(c.dailyRate)}<span> /day</span></div>
                  <button className="accent small" onClick={() => openBooking(c)}>Book this car</button>
                </div>
              );
            })}
          </div>
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
                  value={`${bookingDraft.car.make} ${bookingDraft.car.model}${bookingDraft.car.year ? ` (${bookingDraft.car.year})` : ''} · ${bookingDraft.car.type} · ${fmtMoney(bookingDraft.car.dailyRate)}/day`}
                  readOnly
                />
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
            {rentalDays(bookingDraft.startDate, bookingDraft.endDate) > 0 && (
              <div className="hint" style={{ marginBottom: 12 }}>
                {rentalDays(bookingDraft.startDate, bookingDraft.endDate)} day(s) × {fmtMoney(bookingDraft.car.dailyRate)} ={' '}
                <strong>{fmtMoney(rentalDays(bookingDraft.startDate, bookingDraft.endDate) * Number(bookingDraft.car.dailyRate))}</strong>
              </div>
            )}
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
