import React from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

function App() {
  return (
    <main className="app">
      <section className="panel">
        <p className="eyebrow">Sistema de Generacion de Horarios</p>
        <h1>Horarios UdeO/UTP</h1>
        <p>Scaffold listo para catalogos, motor y planificacion academica.</p>
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
