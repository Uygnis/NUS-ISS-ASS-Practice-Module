import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useApi } from "../api/useApi";

export default function LoginPage() {
  const { login } = useAuth();
  const api = useApi();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setErr("");
    setBusy(true);
    try {
      const auth = await api.accounts.login({ email, password });
      login(auth);
      if (auth.role === "STAFF") {
        navigate("/fleet");
      } else if (auth.role === "ADMIN") {
        navigate("/admin");
      } else {
        navigate("/browse");
      }
    } catch (e2) {
      setErr(e2.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div id="authScreen">
      <div className="plate">
        <h1>RentEz</h1>
        <div className="tagline">Fleet rental, on the road since today.</div>
        <form onSubmit={handleSubmit}>
          <div className="field">
            <label>Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label>Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <div className="authErr">{err}</div>
          <button
            type="submit"
            className="primary"
            style={{ width: "100%" }}
            disabled={busy}
          >
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>
        <div className="authSwitch">
          New to RentEz? <Link to="/register">Create an account</Link>
        </div>
      </div>
    </div>
  );
}
