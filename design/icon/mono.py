import math
from shapely.geometry import box, Polygon
from shapely import affinity
W,H=24,33.6; X0=54-W/2; TOP=33; SX,SY=0.8,0.6; PIV=(54,82)
def card(rot):
    b=box(X0+1.7,TOP+1.7,X0+W-1.7,TOP+H-1.7).buffer(1.7,quad_segs=6)
    return affinity.rotate(b,rot,origin=PIV)
s=0.78; cx,cy=54,TOP+H/2+0.2
def m(u,v): return (cx+8.3*s-s*u, cy-9*s+s*v)
polys=[[(0,0),(3.2,0),(3.2,18),(0,18)],[(3.2,8.4),(12.6,0),(16.6,0),(6.6,9.3)],[(3.2,9.4),(13,18),(9,18),(3.2,11.6)]]
k=Polygon([m(*p) for p in polys[0]]).union(Polygon([m(*p) for p in polys[1]])).union(Polygon([m(*p) for p in polys[2]]))
k=affinity.rotate(k,16,origin=PIV)
a,b,g=card(-20),card(-2),card(16)
gap=1.3
shape=g.difference(k).union(b.difference(g.buffer(gap))).union(a.difference(b.buffer(gap)).difference(g.buffer(gap)))
shape=affinity.translate(shape,SX,SY)
def ring(r): return 'M'+' L'.join(f'{x:.2f},{y:.2f}' for x,y in list(r.coords)[:-1])+' z'
geoms=getattr(shape,'geoms',[shape])
d=' '.join(ring(p.exterior)+' '+' '.join(ring(i) for i in p.interiors) for p in geoms)
xml=f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Themed (monochrome) icon: the fanned hand of three cards with small gaps between them and the
     backwards K cut out of the front one. Made by design/icon/mono.py from the same geometry as the
     full-color icon (mipmap/ic_launcher_foreground, rendered by design/icon/make.py). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{d}" />
</vector>
'''
open('/home/claude/strike3-app/app/src/main/res/drawable/ic_launcher_foreground.xml','w').write(xml)
open('mono.svg','w').write(f'<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108"><rect width="108" height="108" fill="#334"/><path fill="#fff" fill-rule="evenodd" d="{d}"/></svg>')
print(len(d))
