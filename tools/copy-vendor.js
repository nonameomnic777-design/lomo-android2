// Copia las librerías de lectura y las fuentes a la carpeta "vendor"
// para que Lomo funcione sin internet. Se ejecuta solo después de "npm install".
const fs = require('fs');
const path = require('path');

const raiz = path.join(__dirname, '..');
const nm = path.join(raiz, 'node_modules');
const salida = path.join(raiz, 'vendor');
fs.mkdirSync(salida, { recursive: true });

function copiar(desde, hasta) {
  fs.copyFileSync(path.join(nm, desde), path.join(salida, hasta));
  console.log('Copiado:', hasta);
}

copiar('jszip/dist/jszip.min.js', 'jszip.min.js');
copiar('epubjs/dist/epub.min.js', 'epub.min.js');
copiar('pdfjs-dist/build/pdf.min.js', 'pdf.min.js');
copiar('pdfjs-dist/build/pdf.worker.min.js', 'pdf.worker.min.js');

// Fuentes: se incrustan en un solo archivo para usarlas también dentro de los libros
const familias = [
  { paquete: '@fontsource/literata', nombre: 'Literata', pesos: [[400, 'normal'], [600, 'normal'], [400, 'italic']] },
  { paquete: '@fontsource/schibsted-grotesk', nombre: 'Schibsted Grotesk', pesos: [[400, 'normal'], [500, 'normal'], [600, 'normal'], [800, 'normal']] }
];
let css = '';
for (const f of familias) {
  for (const [peso, estilo] of f.pesos) {
    const archivo = f.paquete.split('/')[1] + '-latin-' + peso + '-' + estilo + '.woff2';
    const ruta = path.join(nm, f.paquete, 'files', archivo);
    if (!fs.existsSync(ruta)) { console.warn('Aviso: falta la fuente', archivo, '(se usará una del sistema)'); continue; }
    const b64 = fs.readFileSync(ruta).toString('base64');
    css += "@font-face{font-family:'" + f.nombre + "';font-style:" + estilo + ';font-weight:' + peso +
      ';font-display:swap;src:url(data:font/woff2;base64,' + b64 + ") format('woff2')}\n";
  }
}
fs.writeFileSync(path.join(salida, 'fonts.js'), 'window.LOMO_FONT_CSS=' + JSON.stringify(css) + ';');
console.log('Fuentes listas.');
