<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{font-family:"Space Grotesk";font-weight:700;src:url("../../../fonts/space-grotesk-latin-700-normal.woff2") format("woff2");}
@font-face{font-family:"Space Grotesk";font-weight:500;src:url("../../../fonts/space-grotesk-latin-500-normal.woff2") format("woff2");}
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:1080px;height:1920px;overflow:hidden;background:#05080F}
.stage{position:relative;width:1080px;height:1920px;overflow:hidden;
 background:radial-gradient(900px 700px at 12% 6%,rgba(0,229,255,.20),transparent 62%),radial-gradient(900px 800px at 95% 98%,rgba(16,185,129,.18),transparent 62%),#05080F}
.cap{position:absolute;left:0;right:0;top:92px;text-align:center;padding:0 60px;font-family:"Space Grotesk",sans-serif}
h1{font-size:104px;line-height:1.04;font-weight:700;color:#F4F7FB;letter-spacing:-2px}
h1 em{font-style:normal;color:#00E5FF;text-shadow:0 0 38px rgba(0,229,255,.55)}
p{margin-top:26px;font-size:40px;font-weight:500;color:#94A3B8}
.band{position:absolute;left:0;width:1080px;overflow:hidden;background:#0A1020}
.band img{position:absolute;left:0;width:1080px;height:auto;display:block}
svg.lines{position:absolute;left:0;top:0;width:1080px;height:1920px;pointer-events:none}
.pill{position:absolute;left:44px;font-family:"Space Grotesk",sans-serif;font-weight:700;font-size:34px;letter-spacing:1px;color:#05080F;padding:10px 26px;border-radius:999px;box-shadow:0 8px 30px rgba(0,0,0,.5)}
</style></head><body><div class="stage">
<div class="cap"><h1>{{CAPTION}}</h1><p>{{SUB}}</p></div>
{{BANDS}}
<svg class="lines">{{LINES}}</svg>
{{PILLS}}
</div></body></html>
