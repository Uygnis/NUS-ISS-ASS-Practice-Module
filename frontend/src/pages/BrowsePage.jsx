import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApi } from '../api/useApi';
import { CAR_TYPES } from '../api/catalog';
import { Message, Empty, fmtMoney } from '../components/ui';

export default function BrowsePage() {
  const api = useApi();
  const navigate = useNavigate();
  const [location, setLocation] = useState('');
  const [type, setType] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [cars, setCars] = useState(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [bookingDraft, setBookingDraft] = useState(null); // {carId, location, startDate, endDate}
  const [bookErr, setBookErr] = useState('');

  async function runSearch(e) {
    e.preventDefault();
    setErr('');
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
    setBookingDraft({ carId, pickupLocation: car.location || '', startDate, endDate });
  }

  async function submitBooking(e) {
    e.preventDefault();
    setBookErr('');
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
            <input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="e.g. Singapore" />
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
            <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          </div>
          <div className="field">
            <label>End date</label>
            <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          </div>
          <div className="field">
            <button type="submit" className="primary" disabled={busy}>{busy ? 'Searching…' : 'Search'}</button>
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
        <div className="panel">
          <h2>New booking</h2>
          <form onSubmit={submitBooking}>
            <div className="row">
              <div className="field">
                <label>Car ID</label>
                <input type="number" value={bookingDraft.carId}
                  onChange={(e) => setBookingDraft((d) => ({ ...d, carId: e.target.value }))} required />
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
                <input type="date" value={bookingDraft.startDate}
                  onChange={(e) => setBookingDraft((d) => ({ ...d, startDate: e.target.value }))} required />
              </div>
              <div className="field">
                <label>End date</label>
                <input type="date" value={bookingDraft.endDate}
                  onChange={(e) => setBookingDraft((d) => ({ ...d, endDate: e.target.value }))} required />
              </div>
            </div>
            <Message text={bookErr} kind="err" />
            <div className="row">
              <button type="submit" className="primary">Confirm booking</button>
              <button type="button" onClick={() => setBookingDraft(null)}>Cancel</button>
            </div>
          </form>
        </div>
      )}
    </>
  );
}
