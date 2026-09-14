import { useEffect, useState } from "react";
import { useApi } from "../api/useApi";
import { CAR_TYPES } from "../api/catalog";
import {
  Message,
  Empty,
  StatusPill,
  fmtMoney,
  fmtDate,
  CAR_STATUS_CLASS,
  MAINT_STATUS_CLASS,
} from "../components/ui";

const SUBS = [
  { id: "cars", label: "Cars" },
  { id: "add", label: "Add a car" },
  { id: "maint", label: "Maintenance" },
  // { id: "stats", label: "Fleet stats" },
];

export default function FleetPage() {
  const [sub, setSub] = useState("cars");
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
      {sub === "cars" && <CarsPanel />}
      {sub === "add" && <AddCarPanel />}
      {sub === "maint" && <MaintenancePanel />}
      {sub === "stats" && <StatsPanel />}
    </>
  );
}

function CarsPanel() {
  const api = useApi();
  const [location, setLocation] = useState("");
  const [type, setType] = useState("");
  const [cars, setCars] = useState(null);
  const [err, setErr] = useState("");

  async function load() {
    setErr("");
    try {
      const list = await api.catalog.search(location, type);
      setCars(list);
    } catch (e) {
      setErr(e.message);
    }
  }
  useEffect(() => {
    load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */
  }, []);

  async function setStatus(id, status) {
    if (!status) return;
    try {
      await api.catalog.setStatus(id, status);
      load();
    } catch (e) {
      setErr(e.message);
    }
  }
  async function remove(id) {
    if (!confirm(`Delete car #${id}? This cannot be undone.`)) return;
    try {
      await api.catalog.remove(id);
      load();
    } catch (e) {
      setErr(e.message);
    }
  }

  return (
    <div className="panel">
      <h2>Fleet</h2>
      <div className="inline-form">
        <div className="field">
          <label>Location</label>
          <input
            value={location}
            onChange={(e) => setLocation(e.target.value)}
          />
        </div>
        <div className="field">
          <label>Type</label>
          <select value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">Any</option>
            {CAR_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <button type="button" className="primary" onClick={load}>
            Filter
          </button>
        </div>
      </div>
      <Message text={err} kind="err" />
      {cars === null ? (
        <div className="hint">Loading…</div>
      ) : cars.length === 0 ? (
        <Empty>No cars found.</Empty>
      ) : (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Make/model</th>
              <th>Year</th>
              <th>Type</th>
              <th>Location</th>
              <th>Rate</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {cars.map((c) => (
              <tr key={c.id}>
                <td className="mono">#{c.id}</td>
                <td>
                  {c.make} {c.model}
                </td>
                <td>{c.year || ""}</td>
                <td>{c.type}</td>
                <td>{c.location}</td>
                <td>{fmtMoney(c.dailyRate)}</td>
                <td>
                  <StatusPill status={c.status} map={CAR_STATUS_CLASS} />
                </td>
                <td>
                  <select
                    defaultValue=""
                    onChange={(e) => {
                      setStatus(c.id, e.target.value);
                      e.target.value = "";
                    }}
                  >
                    <option value="">Set status…</option>
                    {["AVAILABLE", "RENTED", "MAINTENANCE", "RETIRED"].map(
                      (s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ),
                    )}
                  </select>{" "}
                  <button className="small danger" onClick={() => remove(c.id)}>
                    Delete
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

function AddCarPanel() {
  const api = useApi();
  const [form, setForm] = useState({
    make: "",
    model: "",
    year: "",
    type: CAR_TYPES[0],
    dailyRate: "",
    location: "",
  });
  const [msg, setMsg] = useState(null);

  function set(field) {
    return (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  }

  async function submit(e) {
    e.preventDefault();
    setMsg(null);
    try {
      const c = await api.catalog.create({
        make: form.make,
        model: form.model,
        year: Number(form.year) || undefined,
        type: form.type,
        dailyRate: Number(form.dailyRate),
        location: form.location,
      });
      setMsg({
        text: `Added ${c.make} ${c.model} as car #${c.id}.`,
        kind: "ok",
      });
      setForm({
        make: "",
        model: "",
        year: "",
        type: CAR_TYPES[0],
        dailyRate: "",
        location: "",
      });
    } catch (e2) {
      setMsg({ text: e2.message, kind: "err" });
    }
  }

  return (
    <div className="panel">
      <h2>Add a car to the fleet</h2>
      <form onSubmit={submit}>
        <div className="row">
          <div className="field">
            <label>Make</label>
            <input value={form.make} onChange={set("make")} required />
          </div>
          <div className="field">
            <label>Model</label>
            <input value={form.model} onChange={set("model")} required />
          </div>
          <div className="field">
            <label>Year</label>
            <input type="number" value={form.year} onChange={set("year")} />
          </div>
        </div>
        <div className="row">
          <div className="field">
            <label>Type</label>
            <select value={form.type} onChange={set("type")} required>
              {CAR_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>Daily rate</label>
            <input
              type="number"
              step="0.01"
              value={form.dailyRate}
              onChange={set("dailyRate")}
              required
            />
          </div>
          <div className="field">
            <label>Location</label>
            <input value={form.location} onChange={set("location")} required />
          </div>
        </div>
        {msg && <Message text={msg.text} kind={msg.kind} />}
        <button type="submit" className="primary">
          Add car
        </button>
      </form>
    </div>
  );
}

function MaintenancePanel() {
  const api = useApi();
  const [form, setForm] = useState({
    carId: "",
    scheduledDate: "",
    description: "",
  });
  const [msg, setMsg] = useState(null);
  const [histCarId, setHistCarId] = useState("");
  const [history, setHistory] = useState(null);
  const [histErr, setHistErr] = useState("");

  function set(field) {
    return (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  }

  async function submit(e) {
    e.preventDefault();
    setMsg(null);
    try {
      const m = await api.catalog.scheduleMaintenance({
        carId: Number(form.carId),
        scheduledDate: form.scheduledDate,
        description: form.description,
      });
      setMsg({
        text: `Maintenance #${m.id} scheduled for car #${m.carId}.`,
        kind: "ok",
      });
      setForm({ carId: "", scheduledDate: "", description: "" });
    } catch (e2) {
      setMsg({ text: e2.message, kind: "err" });
    }
  }

  async function loadHistory() {
    if (!histCarId) {
      setHistory(null);
      return;
    }
    setHistErr("");
    try {
      const list = await api.catalog.maintenanceFor(histCarId);
      setHistory(list);
    } catch (e) {
      setHistErr(e.message);
    }
  }

  async function setMaintStatus(id, status) {
    if (!status) return;
    try {
      await api.catalog.maintenanceStatus(id, status);
      loadHistory();
    } catch (e) {
      setHistErr(e.message);
    }
  }

  return (
    <>
      <div className="panel">
        <h2>Schedule maintenance</h2>
        <form className="inline-form" onSubmit={submit}>
          <div className="field">
            <label>Car ID</label>
            <input
              type="number"
              value={form.carId}
              onChange={set("carId")}
              required
            />
          </div>
          <div className="field">
            <label>Scheduled date</label>
            <input
              type="date"
              value={form.scheduledDate}
              onChange={set("scheduledDate")}
              required
            />
          </div>
          <div className="field" style={{ flex: 2 }}>
            <label>Description</label>
            <input
              value={form.description}
              onChange={set("description")}
              required
            />
          </div>
          <div className="field">
            <button type="submit" className="primary">
              Schedule
            </button>
          </div>
        </form>
        {msg && <Message text={msg.text} kind={msg.kind} />}
      </div>
      <div className="panel">
        <h2>Maintenance history for a car</h2>
        <div className="inline-form">
          <div className="field">
            <label>Car ID</label>
            <input
              type="number"
              value={histCarId}
              onChange={(e) => setHistCarId(e.target.value)}
            />
          </div>
          <div className="field">
            <button type="button" className="primary" onClick={loadHistory}>
              Look up
            </button>
          </div>
        </div>
        <Message text={histErr} kind="err" />
        {history &&
          (history.length === 0 ? (
            <Empty>No maintenance records for this car.</Empty>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Description</th>
                  <th>Scheduled</th>
                  <th>Completed</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {history.map((m) => (
                  <tr key={m.id}>
                    <td className="mono">#{m.id}</td>
                    <td>{m.description}</td>
                    <td>{fmtDate(m.scheduledDate)}</td>
                    <td>{fmtDate(m.completedDate)}</td>
                    <td>
                      <StatusPill status={m.status} map={MAINT_STATUS_CLASS} />
                    </td>
                    <td>
                      <select
                        defaultValue=""
                        onChange={(e) => {
                          setMaintStatus(m.id, e.target.value);
                          e.target.value = "";
                        }}
                      >
                        <option value="">Set status…</option>
                        {["SCHEDULED", "IN_PROGRESS", "COMPLETED"].map((s) => (
                          <option key={s} value={s}>
                            {s}
                          </option>
                        ))}
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ))}
      </div>
    </>
  );
}

// function StatsPanel() {
//   const api = useApi();
//   const [stats, setStats] = useState(null);
//   const [err, setErr] = useState('');

//   useEffect(() => {
//     api.catalog.stats().then(setStats).catch((e) => setErr(e.message));
//     // eslint-disable-next-line react-hooks/exhaustive-deps
//   }, []);

//   return (
//     <div className="panel">
//       <h2>Fleet stats</h2>
//       <Message text={err} kind="err" />
//       {!stats ? <div className="hint">Loading…</div> : (
//         <div className="cardgrid">
//           <StatCard label="Total cars" value={stats.totalCars} />
//           <StatCard label="Available" value={stats.availableCars} />
//           <StatCard label="In maintenance" value={stats.inMaintenanceCars} />
//         </div>
//       )}
//     </div>
//   );
// }

function StatCard({ label, value }) {
  return (
    <div className="carcard">
      <div className="hint">{label}</div>
      <div className="rate">{value ?? "—"}</div>
    </div>
  );
}

export { StatCard };
