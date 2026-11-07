// Reúne en "www" lo que entra en el APK: la app, las imágenes y las librerías (vendor).
const fs = require('fs'), path = require('path');
const raiz = path.join(__dirname, '..'), www = path.join(raiz, 'www');
fs.rmSync(www, { recursive: true, force: true });
fs.mkdirSync(www, { recursive: true });
for (const x of ['index.html', 'assets', 'icons', 'vendor']) {
  const o = path.join(raiz, x);
  if (!fs.existsSync(o)) { console.error('Falta ' + x + (x === 'vendor' ? ' (¿falló npm install?)' : '')); process.exit(1); }
  fs.cpSync(o, path.join(www, x), { recursive: true });
}
for (const f of ['fonts.js', 'jszip.min.js', 'epub.min.js', 'pdf.min.js', 'pdf.worker.min.js']) {
  if (!fs.existsSync(path.join(www, 'vendor', f))) { console.error('Falta vendor/' + f); process.exit(1); }
}
console.log('www listo');
