/*
 * Servidor del frontend de GDI para la laptop (capa de presentación).
 *
 * - Entrega la página (index.html, css, js, img) en el puerto 5500.
 * - Reenvía todo lo que empiece con /api al backend de Azure. Así el teléfono solo habla con la laptop y funciona
 *   aunque la página se abra con https (por ejemplo a través de un túnel), porque la laptop es la que llama al
 *   backend por http.
 *
 * Uso (en la carpeta frontend):   node servidor.js
 * Otro backend:                   set GDI_BACKEND=http://IP:8080   (PowerShell: $env:GDI_BACKEND="http://IP:8080")
 * No necesita instalar nada: solo Node.js.
 */
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const PUERTO = Number(process.env.PORT || 5500);
const BACKEND = new URL(process.env.GDI_BACKEND || 'http://68.211.137.169:8080');
const RAIZ = __dirname;
const TIPOS = {
    '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
    '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
    '.webp': 'image/webp', '.json': 'application/json',
};

function reenviarAlBackend(req, res) {
    const destino = new URL(req.url, BACKEND);
    const cabeceras = { ...req.headers, host: destino.host };
    delete cabeceras.origin;      // la petición ya no viene del navegador sino de la laptop
    delete cabeceras.referer;
    cabeceras['x-forwarded-for'] = req.socket.remoteAddress;
    const salida = http.request(destino, { method: req.method, headers: cabeceras }, respuesta => {
        res.writeHead(respuesta.statusCode, respuesta.headers);
        respuesta.pipe(res);
    });
    salida.on('error', () => {
        res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ status: 502, mensaje: `La laptop no pudo conectarse con el backend (${BACKEND.origin}). Revisa que la VM esté encendida.` }));
    });
    req.pipe(salida);
}

function entregarArchivo(req, res) {
    const ruta = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    // config.js: la página usa rutas relativas (/api) y este servidor las reenvía al backend.
    if (ruta === '/js/config.js') {
        res.writeHead(200, { 'Content-Type': TIPOS['.js'], 'Cache-Control': 'no-store' });
        return res.end("window.GDI_API_URL = '/api';\n");
    }
    const archivo = path.normalize(path.join(RAIZ, ruta === '/' ? 'index.html' : ruta));
    if (!archivo.startsWith(RAIZ + path.sep) || path.basename(archivo) === 'servidor.js') {
        res.writeHead(403); return res.end('Prohibido');
    }
    fs.readFile(archivo, (err, datos) => {
        if (err) { res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }); return res.end('No encontrado'); }
        res.writeHead(200, { 'Content-Type': TIPOS[path.extname(archivo).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
        res.end(datos);
    });
}

http.createServer((req, res) => {
    const inicio = Date.now();
    res.on('finish', () => {
        if (req.url.startsWith('/api')) console.log(`${new Date().toLocaleTimeString('es-MX')}  ${req.method} ${req.url} -> ${res.statusCode} (${Date.now() - inicio} ms)`);
    });
    if (req.url.startsWith('/api/') || req.url === '/api') return reenviarAlBackend(req, res);
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); return res.end(); }
    entregarArchivo(req, res);
}).listen(PUERTO, '0.0.0.0', () => {
    const ips = Object.values(os.networkInterfaces()).flat().filter(i => i && i.family === 'IPv4' && !i.internal).map(i => i.address);
    console.log('Frontend de GDI corriendo (Ctrl + C para detener)');
    console.log(`  En esta laptop:            http://localhost:${PUERTO}`);
    ips.forEach(ip => console.log(`  Teléfono en el mismo Wi-Fi: http://${ip}:${PUERTO}`));
    console.log(`  Backend (API):             ${BACKEND.origin}`);
});
