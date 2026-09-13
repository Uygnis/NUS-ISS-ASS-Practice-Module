import { useEffect, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useApi } from "../api/useApi";
import ConfigPanel from "./ConfigPanel";

const TABS = [
  { to: "/browse", label: "Browse & book", roles: ["CUSTOMER"] },
  { to: "/bookings", label: "My bookings", roles: ["CUSTOMER"] },
  { to: "/payments", label: "Payments", roles: ["CUSTOMER"] },
  {
    to: "/notifications",
    label: "Notifications",
    roles: ["CUSTOMER", "STAFF", "ADMIN"],
    badge: true,
  },
  { to: "/profile", label: "Profile", roles: ["CUSTOMER", "STAFF", "ADMIN"] },
  { to: "/fleet", label: "Fleet management", roles: ["STAFF"] },
  { to: "/admin", label: "Administration", roles: ["ADMIN"] },
];

export default function Layout() {
  const { auth, logout } = useAuth();
  const api = useApi();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function poll() {
      try {
        const res = await api.notifications.unreadCount();
        const val =
          res && typeof res === "object" ? Object.values(res)[0] : res;
        if (!cancelled) setUnread(val || 0);
      } catch {
        /* silent — badge just stays as-is */
      }
    }
    poll();
    const id = setInterval(poll, 30000);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth?.token]);

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <div id="appShell">
      <header className="topband">
        <NavLink to="/browse" className="brand">
          RentEz
        </NavLink>
        <div className="who">
          <span>{auth?.fullName}</span>
          <span className="rolebadge">{auth?.role}</span>
          <button className="small" onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </header>
      <nav className="tabs">
        {TABS.filter((t) => t.roles.includes(auth?.role)).map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            className={({ isActive }) => (isActive ? "active" : "")}
          >
            {t.label}
            {t.badge && unread > 0 ? (
              <span className="badge-count">{unread}</span>
            ) : null}
          </NavLink>
        ))}
      </nav>
      <main>
        <Outlet />
      </main>
      <ConfigPanel />
    </div>
  );
}
