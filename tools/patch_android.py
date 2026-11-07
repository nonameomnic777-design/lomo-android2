# Personaliza el proyecto Android que genera "npx cap add android": iconos, color, pantalla de inicio y copia de seguridad.
# Usa solo la biblioteca estándar de Python. Si algo no está donde se espera, avisa y sigue (el APK sale con el aspecto por defecto).
import os, re, shutil, glob, sys
raiz = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
res = os.path.join(raiz, 'android', 'app', 'src', 'main', 'res')
man = os.path.join(raiz, 'android', 'app', 'src', 'main', 'AndroidManifest.xml')
mine = os.path.join(raiz, 'android-res')
if not os.path.isdir(res):
    print('ERROR: no existe', res, '- ¿se ejecutó "npx cap add android"?'); sys.exit(1)
bg = open(os.path.join(mine, 'background.txt')).read().strip()

# 1) iconos (normal, redondo y primer plano del icono adaptativo)
n = 0
for d in sorted(glob.glob(os.path.join(mine, 'mipmap-*'))):
    dest = os.path.join(res, os.path.basename(d)); os.makedirs(dest, exist_ok=True)
    for f in os.listdir(d):
        shutil.copy(os.path.join(d, f), os.path.join(dest, f)); n += 1
print('iconos copiados:', n)

# 2) color de fondo del icono adaptativo
cf = os.path.join(res, 'values', 'ic_launcher_background.xml')
if os.path.exists(cf):
    t = open(cf, encoding='utf8').read()
    t2 = re.sub(r'(<color name="ic_launcher_background">)[^<]*(</color>)', r'\g<1>' + bg + r'\g<2>', t)
    open(cf, 'w', encoding='utf8').write(t2); print('color de fondo:', bg, '(cambiado)' if t2 != t else '(sin cambios)')
else:
    print('AVISO: no hay ic_launcher_background.xml; se deja el icono adaptativo por defecto')

# 3) pantalla de inicio: color liso con el logo en el centro (sustituye al logo de Capacitor)
pngs = glob.glob(os.path.join(res, 'drawable*', 'splash.png'))
if pngs:
    for p in pngs: os.remove(p)
    os.makedirs(os.path.join(res, 'drawable-nodpi'), exist_ok=True)
    shutil.copy(os.path.join(mine, 'drawable-nodpi', 'lomo_splash_logo.png'), os.path.join(res, 'drawable-nodpi', 'lomo_splash_logo.png'))
    os.makedirs(os.path.join(res, 'drawable'), exist_ok=True)
    open(os.path.join(res, 'drawable', 'splash.xml'), 'w', encoding='utf8').write(
        '<?xml version="1.0" encoding="utf-8"?>\n<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '  <item><color android:color="#EEF1F4"/></item>\n'
        '  <item android:gravity="center"><bitmap android:src="@drawable/lomo_splash_logo" android:gravity="center"/></item>\n</layer-list>\n')
    print('pantalla de inicio cambiada (%d imágenes sustituidas)' % len(pngs))
else:
    print('AVISO: no se encontró splash.png; se deja la pantalla de inicio por defecto')

# 4) sin copia de seguridad automática a la nube de Google (los libros son tuyos y pesan mucho)
if os.path.exists(man):
    t = open(man, encoding='utf8').read()
    t2 = t.replace('android:allowBackup="true"', 'android:allowBackup="false"')
    open(man, 'w', encoding='utf8').write(t2); print('allowBackup:', 'desactivado' if t2 != t else 'ya estaba / no encontrado')
