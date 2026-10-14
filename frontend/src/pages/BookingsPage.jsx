import { useEffect, useState, Fragment } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useApi } from "../api/useApi";
import { Message, Empty, Skeleton, StatusPill } from "../components/ui";
import { fmtMoney, fmtDate, BOOKING_STATUS_CLASS, todayISO, dateRangeError } from "../components/format";

function lockedReason(b) {
  if (b.status === "CONFIRMED") return "Paid bookings can't be changed or cancelled here";
  if (b.status === "CANCELLED") return "This booking was cancelled";
  return "";
}

export default function BookingsPage() {
  const api = useApi();
  const navigate = useNavigate();
  const routerLocation = useLocation();
  const [bookings, setBookings] = useState(null);
  const [err, setErr] = useState("");
  const [editingId, setEditingId] = useState(null);
  const [editDraft, setEditDraft] = useState(null);
  const justCreated = routerLocation.state?.justCreated;
  // Seeded from the navigation state rather than set in an effect.
  const [notice, setNotice] = useState(
    justCreated ? `Booking #${justCreated} created. Pay to confirm it.` : "",
  );
  const [editErr, setEditErr] = useState("");
  const [savingEdit, setSavingEdit] = useState(false);

  // load() asks for a refetch; the effect does it. Fetching inside the
  // effect (not via a local async function) lets a stale response be ignored.
  const [reloadKey, setReloadKey] = useState(0);
  const load = () => setReloadKey((k) => k + 1);

  useEffect(() => {
    let active = true;
    api.reservations.mine()
      .then((data) => {
        if (!active) return;
        setErr("");
        setBookings(data);
      })
      .catch((e) => {
        if (active) setErr(e.message);
      });
    return () => {
      active = false;
    };
  }, [api, reloadKey]);

  function startEdit(b) {
    setEditErr("");
    setEditingId(b.id);
    setEditDraft({
      startDate: b.startDate,
      endDate: b.endDate,
      pickupLocation: b.pickupLocation || "",
    });
  }

  async function saveEdit(id, originalStart) {
    // A booking already under way keeps its past start date; only a moved
    // start has to be today or later (same rule as the backend).
    const rangeErr = dateRangeError(editDraft.startDate, editDraft.endDate, {
      allowPastStart: editDraft.startDate === originalStart,
    });
    if (rangeErr) {
      setEditErr(rangeErr);
      return;
    }
    setEditErr("");
    setSavingEdit(true);
    try {
      await api.reservations.update(id, editDraft);
      setEditingId(null);
      setNotice(`Booking #${id} updated.`);
      load();
    } catch (e) {
      setEditErr(e.message);
    } finally {
      setSavingEdit(false);
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
    navigate("/payments", { state: { bookingId } });
  }

  return (
    <div className="panel">
      <h2>My bookings</h2>
      {justCreated && notice.startsWith(`Booking #${justCreated} created`) ? (
        <Message text={notice} kind="ok" sticky>
          <button className="small accent" onClick={() => goPay(justCreated)}>
            Pay now
          </button>
        </Message>
      ) : (
        <Message text={notice} kind="ok" />
      )}
      <Message text={err} kind="err" />
      {bookings === null ? (
        <Skeleton rows={4} />
      ) : bookings.length === 0 ? (
        <Empty action={{ label: "Browse cars", to: "/browse" }}>
          You have no bookings yet.
        </Empty>
      ) : (
        <table className="stack">
          <thead>
            <tr>
              <th>ID</th>
              <th>Car</th>
              <th>Dates</th>
              <th>Pickup</th>
              <th>Total</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {bookings.map((b) => (
              <Fragment key={b.id}>
                <tr>
                  <td data-label="ID" className="mono">#{b.id}</td>
                  <td data-label="Car">
                    {b.carMake} {b.carModel}
                    <div className="hint">{b.carType}</div>
                  </td>
                  <td data-label="Dates">
                    {fmtDate(b.startDate)} → {fmtDate(b.endDate)}
                  </td>
                  <td data-label="Pickup">{b.pickupLocation || "—"}</td>
                  <td data-label="Total">{fmtMoney(b.totalAmount)}</td>
                  <td data-label="Status">
                    <StatusPill status={b.status} map={BOOKING_STATUS_CLASS} />
                  </td>
                  <td data-label="Actions">
                    <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                      <button
                        className="small"
                        onClick={() => startEdit(b)}
                        title={lockedReason(b) || "Change the dates or pickup location"}
                        disabled={
                          b.status === "CONFIRMED" || b.status === "CANCELLED"
                        }
                      >
                        Modify
                      </button>
                      <button
                        className="small danger"
                        onClick={() => cancelBooking(b.id)}
                        title={lockedReason(b) || "Cancel this booking"}
                        disabled={
                          b.status === "CONFIRMED" || b.status === "CANCELLED"
                        }
                      >
                        Cancel
                      </button>
                      <button
                        className="small accent"
                        onClick={() => goPay(b.id)}
                        title={
                          b.status === "PENDING_PAYMENT"
                            ? "Pay for this booking"
                            : "Only bookings awaiting payment can be paid"
                        }
                        disabled={b.status !== "PENDING_PAYMENT"}
                      >
                        Pay
                      </button>
                    </div>
                  </td>
                </tr>
                {editingId === b.id && (
                  <tr>
                    <td colSpan={7}>
                      <form
                        className="inline-form"
                        style={{ margin: "6px 0" }}
                        onSubmit={(e) => {
                          e.preventDefault();
                          saveEdit(b.id, b.startDate);
                        }}
                      >
                        <div className="field">
                          <label>Start date</label>
                          <input
                            type="date"
                            value={editDraft.startDate}
                            min={b.startDate < todayISO() ? b.startDate : todayISO()}
                            onChange={(e) =>
                              setEditDraft((d) => ({
                                ...d,
                                startDate: e.target.value,
                              }))
                            }
                          />
                        </div>
                        <div className="field">
                          <label>End date</label>
                          <input
                            type="date"
                            value={editDraft.endDate}
                            min={editDraft.startDate}
                            onChange={(e) =>
                              setEditDraft((d) => ({
                                ...d,
                                endDate: e.target.value,
                              }))
                            }
                          />
                        </div>
                        <div className="field">
                          <label>Pickup location</label>
                          <input
                            value={editDraft.pickupLocation}
                            onChange={(e) =>
                              setEditDraft((d) => ({
                                ...d,
                                pickupLocation: e.target.value,
                              }))
                            }
                          />
                        </div>
                        <div className="field">
                          <button
                            type="submit"
                            className="primary small"
                            disabled={savingEdit}
                          >
                            {savingEdit ? "Saving…" : "Save"}
                          </button>
                        </div>
                        <div className="field">
                          <button
                            type="button"
                            className="small"
                            onClick={() => setEditingId(null)}
                          >
                            Cancel
                          </button>
                        </div>
                      </form>
                      <Message text={editErr} kind="err" />
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
