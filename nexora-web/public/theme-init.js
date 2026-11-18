// Aplica o tema salvo antes da pintura (evita flash de tema errado).
// Arquivo externo e síncrono de propósito: a CSP de produção usa script-src 'self', sem 'unsafe-inline'.
try {
  var t = localStorage.getItem('nexora-theme');
  var dark = t ? t === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
  if (dark) document.documentElement.classList.add('dark');
} catch (e) {}
