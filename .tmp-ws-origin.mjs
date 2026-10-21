async function test(origin) {
  return new Promise((resolve) => {
    const ws = new WebSocket('ws://127.0.0.1:8000/api/v1/pose/stream?scenicAreaId=1&deviceId=RT-TS-05', { headers: origin ? { Origin: origin } : undefined });
    const t = setTimeout(() => { try { ws.close(); } catch {} resolve('TIMEOUT'); }, 5000);
    ws.onopen = () => { clearTimeout(t); ws.close(); resolve('OPEN'); };
    ws.onerror = (e) => { clearTimeout(t); resolve('ERR ' + e.message); };
    ws.onclose = (e) => { clearTimeout(t); resolve(`CLOSE code=${e.code}`); };
  });
}
(async () => {
  console.log('no-origin :', await test(null));
  console.log('localhost :', await test('http://localhost:5173'));
  console.log('127.0.0.1 :', await test('http://127.0.0.1:5173'));
  console.log('lan-ip    :', await test('http://192.168.1.100:5173'));
})();
