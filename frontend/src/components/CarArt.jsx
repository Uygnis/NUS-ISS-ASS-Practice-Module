// Side-profile silhouettes per car type for the Browse cards. Inline SVG in
// the existing palette tokens: no image files, no backend or schema change.

const BODIES = {
  SEDAN: 'M20 62 L28 47 L70 42 L95 26 L158 26 L184 42 L216 46 L222 62 Z',
  HATCHBACK: 'M26 62 L30 47 L68 42 L92 24 L176 24 L198 44 L212 48 L214 62 Z',
  SUV: 'M18 62 L20 40 L58 36 L78 18 L192 18 L208 36 L222 40 L222 62 Z',
  TRUCK: 'M16 62 L16 40 L122 40 L122 20 L170 20 L196 38 L224 42 L224 62 Z',
  ELECTRIC: 'M20 62 L26 48 L72 42 L100 26 L156 26 L188 42 L218 47 L222 62 Z',
  LUXURY: 'M12 62 L20 49 L82 42 L112 29 L164 29 L198 44 L228 50 L228 62 Z',
};

const WINDOWS = {
  SEDAN: 'M78 42 L99 30 L154 30 L174 42 Z',
  HATCHBACK: 'M76 42 L96 28 L172 28 L188 42 Z',
  SUV: 'M66 36 L82 22 L188 22 L200 36 Z',
  TRUCK: 'M128 38 L128 24 L166 24 L186 38 Z',
  ELECTRIC: 'M80 42 L103 30 L153 30 L176 42 Z',
  LUXURY: 'M90 42 L115 32 L161 32 L184 42 Z',
};

export default function CarArt({ type }) {
  const key = BODIES[type] ? type : 'SEDAN';
  return (
    <svg className="carart" viewBox="0 0 240 90" role="img" aria-label={`${(type || 'car').toLowerCase()} illustration`}>
      <rect width="240" height="90" fill="var(--paper)" />
      <line x1="0" y1="83" x2="240" y2="83" stroke="var(--steel-light)" strokeWidth="2" strokeDasharray="14 10" />
      <path d={BODIES[key]} fill="var(--sign-green)" />
      <path d={WINDOWS[key]} fill="var(--paper)" opacity="0.85" />
      {[62, 182].map((cx) => (
        <g key={cx}>
          <circle cx={cx} cy="66" r="13" fill="var(--asphalt)" />
          <circle cx={cx} cy="66" r="5" fill="var(--steel-light)" />
        </g>
      ))}
      {key === 'ELECTRIC' && <path d="M126 46 L118 58 L125 58 L120 70 L133 54 L126 54 L131 46 Z" fill="var(--lane-yellow)" />}
      {key === 'LUXURY' && <rect x="214" y="50" width="10" height="4" rx="1" fill="var(--lane-yellow)" />}
    </svg>
  );
}
