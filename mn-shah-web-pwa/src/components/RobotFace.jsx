import React from 'react';

export default function RobotFace({ isAnalyzing, isSpeaking, isListening, direction, statusLabel }) {
  const accent =
    isListening
      ? '#00E5FF'
      : direction === 'UP'
        ? '#00E676'
        : direction === 'DOWN'
          ? '#FF3B5C'
          : '#EEFF00';

  return (
    <div
      className="panel"
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 8,
        borderColor: `${accent}66`,
        background: 'radial-gradient(circle at 50% 25%, rgba(238,255,0,0.08), var(--panel) 70%)'
      }}
    >
      <svg viewBox="0 0 220 112" width="190" height="96" aria-label="MN SHAH AI Robot Face">
        <line x1="110" y1="6" x2="110" y2="20" stroke={accent} strokeWidth="3" strokeLinecap="round" />
        <circle cx="110" cy="6" r="4.5" fill={accent} />
        <rect
          x="30"
          y="20"
          width="160"
          height="84"
          rx="22"
          fill="#06070A"
          stroke={accent}
          strokeWidth="2.5"
        />
        <rect x="44" y="34" width="132" height="56" rx="14" fill="#0E1219" stroke="#283246" strokeWidth="1.5" />
        <circle
          cx="82"
          cy="56"
          r={isAnalyzing ? 10 : 8}
          fill={accent}
          style={{ transition: 'r 0.25s ease' }}
        />
        <circle
          cx="138"
          cy="56"
          r={isAnalyzing ? 10 : 8}
          fill={accent}
          style={{ transition: 'r 0.25s ease' }}
        />
        {isSpeaking ? (
          <g fill="#00E5FF">
            <rect x="76" y="72" width="8" height="10" rx="3" />
            <rect x="90" y="68" width="8" height="14" rx="3" />
            <rect x="104" y="70" width="8" height="12" rx="3" />
            <rect x="118" y="67" width="8" height="15" rx="3" />
            <rect x="132" y="72" width="8" height="10" rx="3" />
          </g>
        ) : (
          <rect x="78" y="74" width="64" height="5" rx="2.5" fill={isListening ? '#00E5FF' : accent} />
        )}
      </svg>
      <div
        className="mono"
        style={{
          fontSize: 11,
          fontWeight: 700,
          letterSpacing: '0.09em',
          color: accent
        }}
      >
        {statusLabel}
      </div>
    </div>
  );
}
