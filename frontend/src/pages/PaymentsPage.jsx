import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useApi } from '../api/useApi';
import { Message, Empty, StatusPill, fmtMoney, PAYMENT_STATUS_CLASS, CONFIRM_STATE_CLASS } from '../components/ui';

export default function PaymentsPage() {
  const api = useApi();
  const routerLocation = useLocation();
  const [bookingId, setBookingId] = useState(routerLocation.state?.bookingId || '');
  const [method, setMethod] = useState('CARD');
  const [cardNumber, setCardNumber] = useState('');
  const [formMsg, setFormMsg] = useState(null); // {text, kind}
  const [payments, setPayments] = useState(null);
  const [listErr, setListErr] = useState('');

  async function loadPayments() {
    setListErr('');
    try {
      const list = await api.payments.mine();
      setPayments(list);
    } catch (e) {
      setListErr(e.message);
    }
  }

  useEffect(() => { loadPayments(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  async function submitPayment(e) {
    e.preventDefault();
    setFormMsg(null);
    try {
      const res = await api.payments.pay({
        bookingId: Number(bookingId),
        method,
        cardNumber: cardNumber || undefined,
      });
      setFormMsg({
        text: `Payment #${res.id} — ${res.status}${res.failureReason ? ' (' + res.failureReason + ')' : ''}`,
        kind: res.status === 'FAILED' ? 'err' : 'ok',
      });
      loadPayments();
    } catch (e2) {
      setFormMsg({ text: e2.message, kind: 'err' });
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
            <label>Booking ID</label>
            <input type="number" value={bookingId} onChange={(e) => setBookingId(e.target.value)} required />
          </div>
          <div className="field">
            <label>Method</label>
            <select value={method} onChange={(e) => setMethod(e.target.value)}>
              <option value="CARD">CARD</option>
              <option value="PAYPAL">PAYPAL</option>
              <option value="WALLET">WALLET</option>
            </select>
          </div>
          <div className="field">
            <label>Card number (if card)</label>
            <input value={cardNumber} onChange={(e) => setCardNumber(e.target.value)} placeholder="optional" />
          </div>
          <div className="field">
            <button type="submit" className="primary">Pay</button>
          </div>
        </form>
        {formMsg && <Message text={formMsg.text} kind={formMsg.kind} />}
      </div>

      <div className="panel">
        <h2>My payments</h2>
        <Message text={listErr} kind="err" />
        {payments === null ? (
          <div className="hint">Loading…</div>
        ) : payments.length === 0 ? (
          <Empty>No payments yet.</Empty>
        ) : (
          <table>
            <thead>
              <tr><th>ID</th><th>Booking</th><th>Amount</th><th>Method</th><th>Status</th><th>Confirm state</th><th>Reference</th><th></th></tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id}>
                  <td className="mono">#{p.id}</td>
                  <td className="mono">#{p.bookingId}</td>
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
