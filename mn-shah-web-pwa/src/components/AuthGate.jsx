import React, { useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import RobotFace from './RobotFace.jsx';

export default function AuthGate({ onAuthenticated }) {
  const [authConfig, setAuthConfig] = useState(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const googleBtnRef = useRef(null);

  useEffect(() => {
    let active = true;
    api
      .getAuthConfig()
      .then((cfg) => {
        if (!active) return;
        setAuthConfig(cfg);
        if (cfg.googleEnabled && cfg.googleClientId && window.google?.accounts?.id && googleBtnRef.current) {
          window.google.accounts.id.initialize({
            client_id: cfg.googleClientId,
            callback: async (response) => {
              setLoading(true);
              setError('');
              try {
                const res = await api.loginWithGoogle(response.credential);
                onAuthenticated(res.user);
              } catch (err) {
                setError(err.message);
              } finally {
                setLoading(false);
              }
            }
          });
          window.google.accounts.id.renderButton(googleBtnRef.current, {
            theme: 'filled_black',
            size: 'large',
            width: 320,
            shape: 'pill',
            text: 'signin_with'
          });
        }
      })
      .catch((err) => {
        if (active) setError(err.message);
      });
    return () => {
      active = false;
    };
  }, [onAuthenticated]);

  async function handlePasswordSubmit(e) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const res = await api.loginWithPassword(email.trim(), password);
      setPassword('');
      onAuthenticated(res.user);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app-shell" style={{ justifyContent: 'center', padding: 18 }}>
      <div className="page-content" style={{ justifyContent: 'center' }}>
        <div style={{ textAlign: 'center', marginBottom: 6 }}>
          <h1 className="brand-title" style={{ fontSize: 28 }}>
            MN SHAH AI
          </h1>
          <div style={{ fontSize: 11, color: 'var(--text-sec)', letterSpacing: '0.12em', marginTop: 4 }}>
            PRIVATE OWNER ACCESS • CHROME PWA
          </div>
        </div>

        <RobotFace
          isAnalyzing={loading}
          isSpeaking={false}
          isListening={false}
          direction={null}
          statusLabel={loading ? 'VERIFYING OWNER SESSION...' : 'OWNER SECURITY GATE'}
        />

        <div className="panel" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ fontSize: 13, color: 'var(--text-sec)', lineHeight: 1.5 }}>
            Authorized Owner Only ({authConfig?.ownerEmailMasked || 'Owner Gmail'}). One-time sign-in keeps
            your secure session active until you log out.
          </div>

          {authConfig?.googleEnabled && (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '4px 0' }}>
              <div ref={googleBtnRef} />
            </div>
          )}

          {authConfig?.passwordEnabled && (
            <form onSubmit={handlePasswordSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <input
                type="email"
                className="input"
                placeholder="Authorized Gmail address"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
              <input
                type="password"
                className="input"
                placeholder="Server-verified owner password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
              <button type="submit" className="btn-primary" disabled={loading}>
                {loading ? 'VERIFYING...' : 'SIGN IN SECURELY'}
              </button>
            </form>
          )}

          {authConfig && !authConfig.googleEnabled && !authConfig.passwordEnabled && (
            <div
              style={{
                padding: 12,
                borderRadius: 12,
                background: 'var(--amber-bg)',
                border: '1px solid var(--amber)',
                fontSize: 12,
                color: 'var(--text)'
              }}
            >
              Server authentication secrets are not configured yet. Set GOOGLE_CLIENT_ID and/or OWNER_PASSWORD
              in Cloud Run environment variables.
            </div>
          )}

          {error && (
            <div
              style={{
                padding: 10,
                borderRadius: 12,
                background: 'var(--red-bg)',
                border: '1px solid var(--red)',
                fontSize: 13,
                color: 'var(--text)'
              }}
            >
              {error}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
