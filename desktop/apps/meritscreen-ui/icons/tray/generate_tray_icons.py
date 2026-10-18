#!/usr/bin/env python3
"""Generate Watching tray icons (stdlib only, no Pillow).

macOS template icons are alpha-only: black pixels + transparency; macOS tints
them for light/dark menu bars. Coloured variants are for Windows/other trays.
Run: python3 icons/tray/generate_tray_icons.py
"""
import math, struct, zlib, os
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)))
os.makedirs(OUT, exist_ok=True)

def cubic(p0,p1,p2,p3,n=28):
    pts=[]
    for i in range(n+1):
        t=i/n; u=1-t
        x=u**3*p0[0]+3*u*u*t*p1[0]+3*u*t*t*p2[0]+t**3*p3[0]
        y=u**3*p0[1]+3*u*u*t*p1[1]+3*u*t*t*p2[1]+t**3*p3[1]
        pts.append((x,y))
    return pts

def shield_poly():
    pts=[]
    pts+=cubic((12,2.6),(14.6,4.1),(17.4,4.9),(19.9,5.1))
    pts+=cubic((19.9,5.1),(19.9,8),(19.9,10.5),(19.9,11.2))
    pts+=cubic((19.9,11.2),(19.9,16.4),(16.9,19.7),(12,21.4))
    pts+=cubic((12,21.4),(7.1,19.7),(4.1,16.4),(4.1,11.2))
    pts+=cubic((4.1,11.2),(4.1,10.5),(4.1,8),(4.1,5.1))
    pts+=cubic((4.1,5.1),(6.6,4.9),(9.4,4.1),(12,2.6))
    return pts
SHIELD=shield_poly()
# inner shield (inset approx toward centroid 12,12)
def inset(poly,f=0.82,c=(12,11.8)):
    return [(c[0]+(x-c[0])*f, c[1]+(y-c[1])*f) for x,y in poly]
INNER=inset(SHIELD)
CHECK=[(8.7,11.9),(11.0,14.2),(15.5,9.1)]
BARS=[(9.7,9.3,11.1,14.3),(13.0,9.3,14.4,14.3)]

def inside(x,y,poly):
    ok=False; j=len(poly)-1
    for i in range(len(poly)):
        xi,yi=poly[i]; xj,yj=poly[j]
        if ((yi>y)!=(yj>y)) and (x < (xj-xi)*(y-yi)/(yj-yi)+xi): ok=not ok
        j=i
    return ok

def segdist(x,y,a,b):
    ax,ay=a; bx,by=b; dx,dy=bx-ax,by-ay
    t=0 if dx*dx+dy*dy==0 else max(0,min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)))
    return math.hypot(x-(ax+t*dx), y-(ay+t*dy))
def checkdist(x,y):
    return min(segdist(x,y,CHECK[0],CHECK[1]), segdist(x,y,CHECK[1],CHECK[2]))
def inbars(x,y):
    return any(x0<=x<=x1 and y0<=y<=y1 for x0,y0,x1,y1 in BARS)

def coverage(px,py,size,mode):
    # returns 0..1 shield-symbol coverage in 24-unit space
    SS=6; hit=0; tot=SS*SS
    scale=24.0/size
    for sy in range(SS):
        for sx in range(SS):
            x=(px+(sx+0.5)/SS)*scale; y=(py+(sy+0.5)/SS)*scale
            if not inside(x,y,SHIELD): continue
            if mode=="protected":
                if checkdist(x,y)<1.05: continue
                hit+=1
            elif mode=="paused":
                # hollow shield outline + pause bars
                if inside(x,y,INNER) and not inbars(x,y): continue
                hit+=1
    return hit/tot

def write_png(path,size,mode,color):
    rows=[]
    for py in range(size):
        row=bytearray([0])
        for px in range(size):
            c=coverage(px,py,size,mode)
            a=int(round(c*255))
            if color is None: r,g,b=0,0,0
            else: r,g,b=color
            # for colored protected: check should be white, handled separately below
            row+=bytes([r,g,b,a])
        rows.append(bytes(row))
    if color is not None and mode=="protected":
        # repaint: white check pixels on teal
        rows=[]
        for py in range(size):
            row=bytearray([0])
            for px in range(size):
                SS=4; shield_hit=0; check_hit=0; tot=SS*SS; scale=24.0/size
                for sy in range(SS):
                    for sx in range(SS):
                        x=(px+(sx+0.5)/SS)*scale; y=(py+(sy+0.5)/SS)*scale
                        if inside(x,y,SHIELD):
                            shield_hit+=1
                            if checkdist(x,y)<1.05: check_hit+=1
                if shield_hit==0: row+=bytes([0,0,0,0])
                elif check_hit>shield_hit*0.5: row+=bytes([255,255,255,255])
                else:
                    a=int(round(shield_hit/tot*255)); row+=bytes([color[0],color[1],color[2],a])
            rows.append(bytes(row))
    if color is not None and mode=="paused":
        rows=[]
        for py in range(size):
            row=bytearray([0])
            for px in range(size):
                c=coverage(px,py,size,mode)
                a=int(round(c*255))
                row+=bytes([color[0],color[1],color[2],a]) if a else bytes([0,0,0,0])
            rows.append(bytes(row))
    raw=b"".join(rows)
    def chunk(t,d): return struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d)&0xffffffff)
    png=b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",size,size,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(raw,9))+chunk(b"IEND",b"")
    open(path,"wb").write(png)
    # stats
    alphas=[]
    print(path, size, mode, len(png))

TEAL=(13,118,110); AMBER=(217,119,6)
write_png(f"{OUT}/shield-template.png",18,"protected",None)
write_png(f"{OUT}/shield-template@2x.png",36,"protected",None)
write_png(f"{OUT}/shield-template-paused.png",18,"paused",None)
write_png(f"{OUT}/shield-template-paused@2x.png",36,"paused",None)
write_png(f"{OUT}/shield-color.png",32,"protected",TEAL)
write_png(f"{OUT}/shield-color@2x.png",64,"protected",TEAL)
write_png(f"{OUT}/shield-color-paused.png",32,"paused",AMBER)
write_png(f"{OUT}/shield-color-paused@2x.png",64,"paused",AMBER)
