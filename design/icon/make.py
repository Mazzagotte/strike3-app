import math, sys
W,H=24,33.6; PX,PY=54,82; TOP=33; X0=54-W/2
K='M0,0 h3.2 v18 h-3.2 z M3.2,8.4 L12.6,0 h4 l-10,9.3 z M3.2,9.4 L13,18 h-4 L3.2,11.6 z'  # normal K, 18 tall; mirrored below
def kpath(cx,cy,s, fill, extra=''):
    # backwards K: mirror horizontally around its center (K box 16.6 x 18)
    return f'<path d="{K}" fill="{fill}" transform="translate({cx+8.3*s},{cy-9*s}) scale({-s},{s})" {extra}/>'
def card(rot, base, edge, inner, layers, k=False, uid='a'):
    x,y=X0,TOP
    clip=f'<clipPath id="c{uid}"><rect x="{x}" y="{y}" width="{W}" height="{H}" rx="1.7"/></clipPath>'
    body=f'''<g transform="rotate({rot} {PX} {PY})">
  {clip}
  <rect x="{x}" y="{y}" width="{W}" height="{H}" rx="1.7" fill="{base}" filter="url(#drop)"/>
  <g clip-path="url(#c{uid})">{layers}</g>
  <rect x="{x+1.7}" y="{y+1.7}" width="{W-3.4}" height="{H-3.4}" rx="0.9" fill="none" stroke="{inner}" stroke-width="0.45"/>
  <rect x="{x+0.15}" y="{y+0.15}" width="{W-0.3}" height="{H-0.3}" rx="1.6" fill="none" stroke="url(#edge)" stroke-width="0.3"/>
  <rect x="{x}" y="{y}" width="{W}" height="{H}" rx="1.7" fill="none" stroke="{edge}" stroke-width="0.25"/>
'''
    if k:
        cx,cy=54,TOP+H/2+0.2; s=0.78
        body+=kpath(cx+0.22,cy+0.28,s,'#5A3C06','opacity=".75"')   # shadow
        body+=kpath(cx-0.16,cy-0.18,s,'#FFF0B8','opacity=".85"')   # highlight
        body+=kpath(cx,cy,s,'url(#kfill)')
    return body+'</g>'
DEFS='''<defs>
<linearGradient id="chromeA" x1="0" y1="0" x2="1" y2="1">
 <stop offset="0" stop-color="#8A9098"/><stop offset=".22" stop-color="#E9ECEF"/><stop offset=".38" stop-color="#A6ACB4"/>
 <stop offset=".55" stop-color="#F7F8FA"/><stop offset=".72" stop-color="#9AA0A8"/><stop offset="1" stop-color="#5F656D"/></linearGradient>
<linearGradient id="chromeB" x1="0" y1="0" x2="1" y2="1">
 <stop offset="0" stop-color="#D8DCE0"/><stop offset=".3" stop-color="#FFFFFF"/><stop offset=".46" stop-color="#B3B9C1"/>
 <stop offset=".62" stop-color="#EEF0F3"/><stop offset=".8" stop-color="#A3A9B1"/><stop offset="1" stop-color="#737981"/></linearGradient>
<linearGradient id="gold" x1="0" y1="0" x2="1" y2="1">
 <stop offset="0" stop-color="#8E6414"/><stop offset=".18" stop-color="#F3D27A"/><stop offset=".36" stop-color="#C9961F"/>
 <stop offset=".52" stop-color="#FCE7A2"/><stop offset=".7" stop-color="#D1A12E"/><stop offset="1" stop-color="#7A5410"/></linearGradient>
<linearGradient id="kfill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#A3780F"/><stop offset="1" stop-color="#6E4C08"/></linearGradient>
<linearGradient id="rainbow" x1="0" y1="1" x2="1" y2="0">
 <stop offset="0" stop-color="#ff5f6d"/><stop offset=".2" stop-color="#ffc371"/><stop offset=".4" stop-color="#9cff8a"/>
 <stop offset=".6" stop-color="#5fd3ff"/><stop offset=".8" stop-color="#b77dff"/><stop offset="1" stop-color="#ff7ad9"/></linearGradient>
<linearGradient id="streak" x1="0" y1="0" x2="1" y2="1">
 <stop offset=".30" stop-color="#fff" stop-opacity="0"/><stop offset=".42" stop-color="#fff" stop-opacity=".55"/>
 <stop offset=".47" stop-color="#fff" stop-opacity=".15"/><stop offset=".55" stop-color="#fff" stop-opacity="0"/></linearGradient>
<linearGradient id="edge" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".9"/><stop offset=".5" stop-color="#fff" stop-opacity=".15"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
<radialGradient id="vig" cx=".35" cy=".25" r=".9"><stop offset="0" stop-color="#fff" stop-opacity=".0"/><stop offset="1" stop-color="#000" stop-opacity=".28"/></radialGradient>
<filter id="drop" x="-40%" y="-40%" width="180%" height="180%">
 <feGaussianBlur in="SourceAlpha" stdDeviation="1.3"/><feOffset dx="0.4" dy="1.4"/><feComponentTransfer><feFuncA type="linear" slope=".65"/></feComponentTransfer>
 <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter>
<filter id="brush" x="0" y="0" width="100%" height="100%">
 <feTurbulence type="fractalNoise" baseFrequency="0.04 1.6" numOctaves="2" seed="3"/><feColorMatrix type="saturate" values="0"/>
 <feComponentTransfer><feFuncA type="linear" slope="0" intercept=".16"/></feComponentTransfer></filter>
<filter id="foil" x="0" y="0" width="100%" height="100%">
 <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="1" seed="9"/>
 <feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 2.6 -1.75"/></filter>
</defs>'''
def chrome_layers(grad,uid):
    x,y=X0,TOP
    return (f'<rect x="{x}" y="{y}" width="{W}" height="{H}" filter="url(#brush)" style="mix-blend-mode:multiply"/>'
            f'<rect x="{x}" y="{y}" width="{W}" height="{H}" fill="url(#streak)"/>'
            f'<rect x="{x+3.2}" y="{y+3.2}" width="{W-6.4}" height="{H-11}" rx=".6" fill="#000" opacity=".07"/>'
            f'<rect x="{x}" y="{y}" width="{W}" height="{H}" fill="url(#vig)"/>')
def gold_layers():
    x,y=X0,TOP
    return (f'<rect x="{x}" y="{y}" width="{W}" height="{H}" fill="url(#rainbow)" opacity=".2" style="mix-blend-mode:soft-light"/>'
            f'<rect x="{x}" y="{y}" width="{W}" height="{H}" fill="url(#rainbow)" opacity=".07" style="mix-blend-mode:color-dodge"/>'
            f'<rect x="{x}" y="{y}" width="{W}" height="{H}" filter="url(#foil)" opacity=".28" style="mix-blend-mode:screen"/>'
            f'<rect x="{x}" y="{y}" width="{W}" height="{H}" fill="url(#streak)"/>'
            f'<rect x="{x}" y="{y}" width="{W}" height="{H}" fill="url(#vig)"/>')
art=(card(-20,'url(#chromeA)','#4E535A','#F2F4F6',chrome_layers('A','a'),uid='a')
    +card(-2,'url(#chromeB)','#5E636A','#FFFFFF',chrome_layers('B','b'),uid='b')
    +card(16,'url(#gold)','#6E4C08','#FFF3C9',gold_layers(),k=True,uid='g'))
def svg(bg=False, size=1024, shift=(0,0)):
    b='<rect width="108" height="108" fill="#0B0C0E"/>' if bg else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">{DEFS}{b}<g transform="translate({shift[0]},{shift[1]})" style="isolation:isolate">{art}</g></svg>'
if __name__=='__main__':
    sx,sy=float(sys.argv[1]),float(sys.argv[2])
    open('fg.svg','w').write(svg(False,1024,(sx,sy)))
    open('full.svg','w').write(svg(True,1024,(sx,sy)))
