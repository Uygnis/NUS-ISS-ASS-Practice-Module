import { useEffect, useState, Fragment } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useApi } from '../api/useApi';
import { Message, Empty, StatusPill, fmtMoney, fmtDate, BOOKING_STATUS_CLASS } from '../components/ui';

export default function BookingsPage() {
  const api = useApi();
  const navigate = useNavigate();
  const routerLocation = useLocation();
  const [bookings, setBookings] = useState(null);
  const [err, setErr] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [editDraft, setEditDraft] = useState(null);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (routerLocation.state?.justCreated) {
      setNotice(`Booking #${routerLocation.state.justCreated} created.`);
    }
  }, [routerLocation.state]);

  async function load() {
    setErr('');
    try {
      const list = await api.reservations.mine();
      setBookings(list);
    } catch (e) {
      setErr(e.message);
    }
  }

  useEffect(() => { load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  function startEdit(b) {
    setEditingId(b.id);
    setEditDraft({ startDate: b.startDate, endDate: b.endDate, pickupLocation: b.pickupLocation || '' });
  }

  async function saveEdit(id) {
    try {
      await api.reservations.update(id, editDraft);
      setEditingId(null);
      load();
    } catch (e) {
      setErr(e.message);
    }
  }

  async function cancelBooking(id) {
    if (!confirm(`Cancel booking #${id}?`)) return;
    try {
      await api.reservations.cancel(id);
      load();
    } catch (e) {
      setErr(e.message);
    }
  }

  function goPay(bookingId) {
    navigate('/payments', { state: { bookingId } });
  }

  return (
    <div className="panel">
      <h2>My bookings</h2>
      <Message text={notice} kind="ok" />
      <Message text={err} kind="err" />
      {bookings === null ? (
        <div className="hint">Loading…</div>
      ) : bookings.length === 0 ? (
        <Empty>No bookings yet. Head to Browse &amp; book to reserve a car.</Empty>
      ) : (
        <table>
          <thead>
            <tr><th>ID</th><th>Car</th><th>Dates</th><th>Pickup</th><th>Total</th><th>Status</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {bookings.map((b) => (
              <Fragment key={b.id}>
                <tr>
                  <td className="mono">#{b.id}</td>
                  <td>{b.carMake} {b.carModel}<div className="hint">{b.carType}</div></td>
                  <td>{fmtDate(b.startDate)} → {fmtDate(b.endDate)}</td>
                  <td>{b.pickupLocation || '—'}</td>
                  <td>{fmtMoney(b.totalAmount)}</td>
                  <td><StatusPill status={b.status} map={BOOKING_STATUS_CLASS} /></td>
                  <td>
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                      <button className="small" onClick={() => startEdit(b)}>Modify</button>
                      <button className="small danger" onClick={() => cancelBooking(b.id)}>Cancel</button>
                      <button className="small accent" onClick={() => goPay(b.id)}>Pay</button>
                    </div>
                  </td>
                </tr>
                {editingId === b.id && (
                  <tr>
                    <td colSpan={7}>
                      <div className="inline-form" style={{ margin: '6px 0' }}>
                        <div className="field">
                          <label>Start date</label>
                          <input type="date" value={editDraft.startDate}
                            onChange={(e) => setEditDraft((d) => ({ ...d, startDate: e.target.value }))} />
                        </div>
                        <div className="field">
                          <label>End date</label>
                          <input type="date" value={editDraft.endDate}
                            onChange={(e) => setEditDraft((d) => ({ ...d, endDate: e.target.value }))} />
                        </div>
                        <div className="field">
                          <label>Pickup location</label>
                          <input value={editDraft.pickupLocation}
                            onChange={(e) => setEditDraft((d) => ({ ...d, pickupLocation: e.target.value }))} />
                        </div>
                        <div className="field">
                          <button className="primary small" onClick={() => saveEdit(b.id)}>Save</button>
                        </div>
                        <div className="field">
                          <button className="small" onClick={() => setEditingId(null)}>Cancel</button>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
