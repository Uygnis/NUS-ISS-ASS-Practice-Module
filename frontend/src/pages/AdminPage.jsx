import { useEffect, useState, useRef } from "react";
import { useApi } from "../api/useApi";
import { Message, Empty, Skeleton, StatusPill } from "../components/ui";
import { fmtMoney, USER_STATUS_CLASS } from "../components/format";
import { StatCard } from "./FleetPage";

const SUBS = [
  { id: "users", label: "Users" },
  { id: "summary", label: "Business summary" },
  { id: "audit", label: "Audit logs" },
];

export default function AdminPage() {
  const [sub, setSub] = useState("users");
  return (
    <>
      <div className="subnav">
        {SUBS.map((s) => (
          <button
            key={s.id}
            className={sub === s.id ? "active" : ""}
            onClick={() => setSub(s.id)}
          >
            {s.label}
          </button>
        ))}
      </div>
      {sub === "users" && <UsersPanel />}
      {sub === "summary" && <SummaryPanel />}
      {sub === "audit" && <AuditPanel />}
    </>
  );
}

function UsersPanel() {
  const api = useApi();
  const [users, setUsers] = useState(null);
  const [err, setErr] = useState("");

  // load() asks for a refetch; the effect does it. Fetching inside the
  // effect (not via a local async function) lets a stale response be ignored.
  const [reloadKey, setReloadKey] = useState(0);
  const load = () => setReloadKey((k) => k + 1);

  useEffect(() => {
    let active = true;
    api.accounts.listUsers()
      .then((data) => {
        if (!active) return;
        setErr("");
        setUsers(data);
      })
      .catch((e) => {
        if (active) setErr(e.message);
      });
    return () => {
      active = false;
    };
  }, [api, reloadKey]);

  async function setRole(id, role) {
    try {
      await api.accounts.setRole(id, role);
      load();
    } catch (e) {
      setErr(e.message);
    }
  }
  async function toggleStatus(id, enabled) {
    try {
      await api.accounts.setStatus(id, enabled);
      load();
    } catch (e) {
      setErr(e.message);
    }
  }

  return (
    <div className="panel">
      <h2>Users</h2>
      <Message text={err} kind="err" />
      {users === null ? (
        <Skeleton rows={4} />
      ) : (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Email</th>
              <th>Phone</th>
              <th>Role</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td className="mono">#{u.id}</td>
                <td>{u.fullName}</td>
                <td>{u.email}</td>
                <td>{u.phone || "—"}</td>
                <td>
                  <select
                    defaultValue={u.role}
                    onChange={(e) => setRole(u.id, e.target.value)}
                  >
                    {["CUSTOMER", "STAFF", "ADMIN"].map((r) => (
                      <option key={r} value={r}>
                        {r}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <StatusPill
                    status={u.enabled ? "ENABLED" : "DISABLED"}
                    map={USER_STATUS_CLASS}
                  />
                </td>
                <td>
                  <button
                    className="small"
                    onClick={() => toggleStatus(u.id, !u.enabled)}
                  >
                    {u.enabled ? "Disable" : "Enable"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function SummaryPanel() {
  const api = useApi();
  const [summary, setSummary] = useState(null);
  const [err, setErr] = useState("");
  const [exporting, setExporting] = useState(false);
  const panelRef = useRef(null);

  useEffect(() => {
    api.accounts
      .reportSummary()
      .then(setSummary)
      .catch((e) => setErr(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleExportPdf = () => {
    // Let the "Preparing…" state paint before the print dialog blocks the thread
    setExporting(true);
    requestAnimationFrame(() => {
      window.print();
      setExporting(false);
    });
  };

  return (
    <div className="panel dashboard" ref={panelRef}>
      <div className="dashboard-header">
        <h2>Business summary</h2>
        <button
          type="button"
          className="btn-export no-print"
          onClick={handleExportPdf}
          disabled={!summary || exporting}
        >
          {exporting ? "Preparing…" : "Export PDF"}
        </button>
      </div>

      <Message text={err} kind="err" />

      {!summary ? (
        <Skeleton rows={4} />
      ) : (
        <>
          {summary.partial && (
            <Message
              text={`Summary is partial — some sections were unavailable: ${(summary.unavailableSections || []).join(", ")}`}
              kind="err"
            />
          )}

          <div className="dashboard-meta">
            Generated {new Date().toLocaleString()}
          </div>

          <div className="cardgrid">
            <StatCard label="Total cars" value={summary.totalCars} />
            <StatCard label="Available cars" value={summary.availableCars} />
            <StatCard
              label="In maintenance"
              value={summary.carsInMaintenance}
            />
            <StatCard label="Total bookings" value={summary.totalBookings} />
            <StatCard
              label="Confirmed bookings"
              value={summary.confirmedBookings}
            />
            <StatCard
              label="Cancelled bookings"
              value={summary.cancelledBookings}
            />
            <StatCard
              label="Total revenue"
              value={
                summary.totalRevenue != null
                  ? fmtMoney(summary.totalRevenue)
                  : null
              }
            />
          </div>

          {summary.bookingsByCarType &&
            Object.keys(summary.bookingsByCarType).length > 0 && (
              <>
                <h3 style={{ marginTop: 18 }}>Bookings by car type</h3>
                <div className="cardgrid">
                  {Object.entries(summary.bookingsByCarType).map(
                    ([type, count]) => (
                      <StatCard key={type} label={type} value={count} />
                    ),
                  )}
                </div>
              </>
            )}
        </>
      )}
    </div>
  );
}

const AUDIT_SOURCES = ["Accounts", "Catalog", "Reservations", "Payments"];

function AuditPanel() {
  const api = useApi();
  const [service, setService] = useState("Accounts");
  const [limit, setLimit] = useState(100);
  const [entries, setEntries] = useState(null);
  const [err, setErr] = useState("");

  // The limit applies when Load is pressed; picking a service loads at once.
  const [appliedLimit, setAppliedLimit] = useState(limit);
  const [reloadKey, setReloadKey] = useState(0);
  const load = () => {
    setAppliedLimit(limit);
    setReloadKey((k) => k + 1);
  };

  useEffect(() => {
    let active = true;
    const sources = {
      Accounts: api.accounts,
      Catalog: api.catalog,
      Reservations: api.reservations,
      Payments: api.payments,
    };
    sources[service]
      .auditLog(appliedLimit)
      .then((data) => {
        if (!active) return;
        setErr("");
        setEntries(data);
      })
      .catch((e) => {
        if (active) setErr(e.message);
      });
    return () => {
      active = false;
    };
  }, [api, service, appliedLimit, reloadKey]);

  return (
    <div className="panel">
      <h2>Audit logs</h2>
      <div className="inline-form">
        <div className="field">
          <label>Service</label>
          <select value={service} onChange={(e) => setService(e.target.value)}>
            {AUDIT_SOURCES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>Limit</label>
          <input
            type="number"
            value={limit}
            onChange={(e) => setLimit(e.target.value)}
          />
        </div>
        <div className="field">
          <button type="button" className="primary" onClick={load}>
            Load
          </button>
        </div>
      </div>
      <Message text={err} kind="err" />
      {entries === null ? (
        <Skeleton rows={4} />
      ) : entries.length === 0 ? (
        <Empty>No audit entries.</Empty>
      ) : (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Entity</th>
              <th>Details</th>
              <th>When</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((a) => (
              <tr key={a.id}>
                <td className="mono">#{a.id}</td>
                <td>{a.actorEmail || "—"}</td>
                <td>{a.action}</td>
                <td className="mono">
                  {a.entityType} #{a.entityId}
                </td>
                <td>{a.details}</td>
                <td>{a.occurredAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
