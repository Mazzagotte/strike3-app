import sys
sys.argv=[sys.argv[0]]
import make
D=make.DEFS
def bar(x,cy,h,w,sl,fill,uid,foil=False):
    pts=f"{x-sl/2},{cy+h/2} {x+w-sl/2},{cy+h/2} {x+w+sl/2},{cy-h/2} {x+sl/2},{cy-h/2}"
    out=f'<clipPath id="b{uid}"><polygon points="{pts}"/></clipPath>'
    out+=f'<polygon points="{pts}" fill="{fill}" filter="url(#drop)"/>'
    bx=f'x="{x-sl}" y="{cy-h/2}" width="{w+2*sl}" height="{h}"'
    out+=f'<g clip-path="url(#b{uid})">'
    if foil:
        out+=f'<rect {bx} fill="url(#rainbow)" opacity=".2" style="mix-blend-mode:soft-light"/><rect {bx} filter="url(#foil)" opacity=".25" style="mix-blend-mode:screen"/>'
    else:
        out+=f'<rect {bx} filter="url(#brush)" style="mix-blend-mode:multiply"/>'
    out+=f'<rect {bx} fill="url(#streak)"/></g>'
    out+=f'<polygon points="{pts}" fill="none" stroke="url(#edge)" stroke-width="0.45"/>'
    return out
def bars(cx,cy,h,w,sl,gap,tag):
    fills=['url(#chromeB)','url(#chromeB)','url(#gold)']
    return ''.join(bar(cx+(i-1)*gap-w/2,cy,h,w,sl,f,f'{tag}{i}',foil=(i==2)) for i,f in enumerate(fills))
def svg(inner,bg=True,size=1024,scale=0.94):
    b='<rect width="108" height="108" fill="#0B0C0E"/>' if bg else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">{D}{b}<g transform="translate(54,54) scale({scale}) translate(-54,-54)" style="isolation:isolate">{inner}</g></svg>'
# C: bars only
C=bars(54,54,42,9.5,11,15,'c')
# D: bars inside a thin gold-framed card outline (one card, no fan)
fr='<rect x="34" y="26" width="40" height="56" rx="3" fill="#111317" stroke="url(#gold)" stroke-width="1.6" filter="url(#drop)"/><rect x="36.6" y="28.6" width="34.8" height="50.8" rx="1.6" fill="none" stroke="#7A5410" stroke-width=".4"/>'
Dd=fr+bars(54,54,30,6.2,7.2,9.2,'d')
open('full_C.svg','w').write(svg(C)); open('full_D.svg','w').write(svg(Dd))
open('fg_C.svg','w').write(svg(C,False)); open('fg_D.svg','w').write(svg(Dd,False))
