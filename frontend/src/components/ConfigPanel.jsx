import { useConfig } from '../context/ConfigContext';

export default function ConfigPanel() {
  const { config, updateConfig } = useConfig();
  return (
    <footer className="cfgfoot">
      <strong style={{ color: 'var(--asphalt)' }}>Service URLs:</strong>
      {Object.keys(config).map((key) => (
        <span key={key}>
          {key}{' '}
          <input
            defaultValue={config[key]}
            onBlur={(e) => updateConfig({ [key]: e.target.value.trim() })}
          />
        </span>
      ))}
    </footer>
  );
}
