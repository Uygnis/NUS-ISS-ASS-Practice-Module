import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useApi } from '../api/useApi';
import { Message, Empty, Skeleton, StatusPill } from '../components/ui';
import { fmtMoney, fmtDate, PAYMENT_STATUS_CLASS, CONFIRM_STATE_CLASS } from '../components/format';

// Only PENDING_PAYMENT can be paid (payment-service BookingView.isAwaitingPayment);
// a MODIFIED booking was already paid before it changed.
const PAYABLE = (b) => b.status === 'PENDING_PAYMENT';

// Just the car. Dates are added only when two payable bookings share a car,
// so the options can still be told apart.
function bookingLabel(b, all) {
  const car = `${b.carMake} ${b.carModel}`;
  const twins = all.filter((x) => `${x.carMake} ${x.carModel}` === car).length > 1;
  return twins ? `${car} (${fmtDate(b.startDate)} → ${fmtDate(b.endDate)})` : car;
}

// 4111111111111111 -> "4111 1111 1111 1111"
function formatCard(value) {
  return value.replace(/\D/g, '').slice(0, 19).replace(/(\d{4})(?=\d)/g, '$1 ');
}

export default function PaymentsPage() {
  const api = useApi();
  const routerLocation = useLocation();
  const [bookingId, setBookingId] = useState(String(routerLocation.state?.bookingId || ''));
  const [bookings, setBookings] = useState(null);
  const [paying, setPaying] = useState(false);
  const [method, setMethod] = useState('CARD');
  const [cardNumber, setCardNumber] = useState('');
  const [formMsg, setFormMsg] = useState(null); // {text, kind}
  const [payments, setPayments] = useState(null);
  const [listErr, setListErr] = useState('');

  // loadPayments()/loadBookings() ask for a refetch; the effects do it, so a
  // stale response can be ignored.
  const [paymentsKey, setPaymentsKey] = useState(0);
  const [bookingsKey, setBookingsKey] = useState(0);
  const loadPayments = () => setPaymentsKey((k) => k + 1);
  const loadBookings = () => setBookingsKey((k) => k + 1);

  useEffect(() => {
    let active = true;
    api.payments
      .mine()
      .then((data) => {
        if (!active) return;
        setListErr('');
        setPayments(data);
      })
      .catch((e) => {
        if (active) setListErr(e.message);
      });
    return () => {
      active = false;
    };
  }, [api, paymentsKey]);

  useEffect(() => {
    let active = true;
    api.reservations
      .mine()
      .then((data) => {
        if (active) setBookings(data);
      })
      .catch((e) => {
        if (!active) return;
        setFormMsg({ text: e.message, kind: 'err' });
        setBookings([]);
      });
    return () => {
      active = false;
    };
  }, [api, bookingsKey]);

  const payable = (bookings || []).filter(PAYABLE);
  const cardDigits = cardNumber.replace(/\D/g, '');
  const byBookingId = Object.fromEntries((bookings || []).map((b) => [b.id, b]));

  async function submitPayment(e) {
    e.preventDefault();
    setFormMsg(null);
    if (method === 'CARD' && (cardDigits.length < 12 || cardDigits.length > 19)) {
      setFormMsg({ text: 'Please enter a valid card number (12 to 19 digits).', kind: 'err' });
      return;
    }
    const booking = byBookingId[bookingId];
    setPaying(true);
    try {
      const res = await api.payments.pay({
        bookingId: Number(bookingId),
        method,
        cardNumber: method === 'CARD' ? cardDigits : undefined,
      });
      const what = booking ? `${booking.carMake} ${booking.carModel}` : 'your booking';
      setFormMsg(res.status === 'FAILED'
        ? { text: `Payment for ${what} was declined${res.failureReason ? ': ' + res.failureReason : ''}. Please check your details or try another method.`, kind: 'err' }
        : { text: `Payment of ${fmtMoney(res.amount)} for ${what} ${res.status === 'SUCCESS' ? 'succeeded' : 'is ' + res.status.toLowerCase()}.`, kind: 'ok' });
      setCardNumber('');
      setBookingId('');
      loadPayments();
      loadBookings();
    } catch (e2) {
      setFormMsg({ text: e2.message, kind: 'err' });
    } finally {
      setPaying(false);
    }
  }

  async function refund(id) {
    if (!confirm(`Refund payment #${id}?`)) return;
    try {
      await api.payments.refund(id);
      loadPayments();
    } catch (e) {
      setFormMsg({ text: e.message, kind: 'err' });
    }
  }

  return (
    <>
      <div className="panel">
        <h2>Make a payment</h2>
        <form className="inline-form" onSubmit={submitPayment}>
          <div className="field">
            <label>Booking</label>
            <select name="bookingId" value={bookingId} onChange={(e) => setBookingId(e.target.value)} required>
              <option value="">
                {bookings === null ? 'Loading your bookings…' : payable.length ? 'Select a booking to pay…' : 'No bookings awaiting payment'}
              </option>
              {payable.map((b) => <option key={b.id} value={String(b.id)}>{bookingLabel(b, payable)}</option>)}
            </select>
          </div>
          <div className="field">
            <label>Amount to pay</label>
            <input value={byBookingId[bookingId] ? fmtMoney(byBookingId[bookingId].totalAmount) : '—'} readOnly
              aria-live="polite" style={{ width: 120, fontWeight: 600 }} />
          </div>
          <div className="field">
            <label>Method</label>
            <select value={method} onChange={(e) => setMethod(e.target.value)}>
              <option value="CARD">Credit / debit card</option>
              <option value="PAYPAL">PayPal</option>
              <option value="WALLET">E-wallet</option>
            </select>
          </div>
          {method === 'CARD' && (
            <div className="field">
              <label>Card number</label>
              <input name="cardNumber" value={cardNumber} onChange={(e) => setCardNumber(formatCard(e.target.value))}
                inputMode="numeric" autoComplete="cc-number" placeholder="1234 5678 9012 3456" required />
            </div>
          )}
          <div className="field">
            <button type="submit" className="primary" disabled={paying || !bookingId}>{paying ? 'Processing…' : 'Pay'}</button>
          </div>
        </form>
        {formMsg && <Message text={formMsg.text} kind={formMsg.kind} />}
      </div>

      <div className="panel">
        <h2>My payments</h2>
        <Message text={listErr} kind="err" />
        {payments === null ? (
          <Skeleton rows={3} />
        ) : payments.length === 0 ? (
          <Empty action={{ label: 'Browse cars', to: '/browse' }}>No payments yet. Book a car to get started.</Empty>
        ) : (
          <table>
            <thead>
              <tr><th>ID</th><th>Booking</th><th>Amount</th><th>Method</th><th>Status</th><th>Confirm state</th><th>Reference</th><th></th></tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id}>
                  <td className="mono">#{p.id}</td>
                  <td>
                    {byBookingId[p.bookingId] ? `${byBookingId[p.bookingId].carMake} ${byBookingId[p.bookingId].carModel}` : `Booking #${p.bookingId}`}
                    {byBookingId[p.bookingId] && <div className="hint">{fmtDate(byBookingId[p.bookingId].startDate)} → {fmtDate(byBookingId[p.bookingId].endDate)}</div>}
                  </td>
                  <td>{fmtMoney(p.amount)} {p.currency}</td>
                  <td>{p.method}</td>
                  <td><StatusPill status={p.status} map={PAYMENT_STATUS_CLASS} /></td>
                  <td><StatusPill status={p.confirmState} map={CONFIRM_STATE_CLASS} /></td>
                  <td className="mono">{p.transactionRef || '—'}</td>
                  <td>{p.status === 'SUCCESS' && <button className="small danger" onClick={() => refund(p.id)}>Refund</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
