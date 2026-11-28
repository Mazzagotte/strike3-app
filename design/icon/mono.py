from shapely.geometry import box, Polygon
from shapely import affinity
sc=0.94
def S(g): return affinity.scale(g,sc,sc,origin=(54,54))
outer=box(34+3,26+3,74-3,82-3).buffer(3,quad_segs=6)
# frame stroke 1.6 centred on the rect edge
ring=box(34+3,26+3,74-3,82-3).buffer(3+0.8,quad_segs=6).difference(box(34+3,26+3,74-3,82-3).buffer(3-0.8,quad_segs=6))
h,w,sl,gap,cx,cy=30,6.2,7.2,9.2,54,54
bars=None
for i in range(3):
    x=cx+(i-1)*gap-w/2
    p=Polygon([(x-sl/2,cy+h/2),(x+w-sl/2,cy+h/2),(x+w+sl/2,cy-h/2),(x+sl/2,cy-h/2)])
    bars=p if bars is None else bars.union(p)
shape=S(ring.union(bars))
def ring_d(r): return 'M'+' L'.join(f'{x:.2f},{y:.2f}' for x,y in list(r.coords)[:-1])+' z'
geoms=getattr(shape,'geoms',[shape])
d=' '.join(ring_d(p.exterior)+' '+' '.join(ring_d(i) for i in p.interiors) for p in geoms)
open('/home/claude/strike3-app/app/src/main/res/drawable/ic_launcher_foreground.xml','w').write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Themed (monochrome) icon: a card outline with the III bars. Made by design/icon/mono.py from the
     same geometry as the full-color icon (mipmap/ic_launcher_foreground, design/icon/barsicon.py). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{d}" />
</vector>
''')
open('monoD.svg','w').write(f'<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108"><rect width="108" height="108" fill="#334"/><path fill="#fff" fill-rule="evenodd" d="{d}"/></svg>')
