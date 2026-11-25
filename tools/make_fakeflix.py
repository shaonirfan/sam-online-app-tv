import os, sys
root = sys.argv[1]
def f(path, data=b"x"):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)
em = f"{root}/DHAKA-FLIX-7/English Movies"
f(f"{em}/(2024) 1080p/Dune Part Two (2024) 1080p/Dune.Part.Two.2024.1080p.WEB-DL.x265.mkv")
f(f"{em}/(2024) 1080p/Dune Part Two (2024) 1080p/Dune.Part.Two.2024.1080p.WEB-DL.x265.srt")
f(f"{em}/(2024) 1080p/Dune Part Two (2024) 1080p/poster.jpg")
f(f"{em}/(2024) 1080p/Inside Out 2 (2024)/Inside.Out.2.2024.mp4")
f(f"{em}/(2024) 1080p/Inside Out 2 (2024)/a.jpg")
f(f"{em}/(2024) 1080p/Kill Part 1 (2024)/Kill.Part.1.2024.mkv")
f(f"{em}/(2023) 1080p/Oppenheimer (2023)/Oppenheimer.2023.1080p.mkv")
# h5ai-style fallback page for the category root (absolute hrefs, parent + asset + sort links)
html = """<html><body><a href="/_h5ai/public/index.html">h5ai</a><a href="?C=N;O=D">Name</a>
<table><tr><td><img src="/_h5ai/public/images/fallback/folder-parent.png"></td><td><a href="/DHAKA-FLIX-7/">Parent Directory</a></td></tr>
<tr><td><img src="/_h5ai/public/images/fallback/folder.png"></td><td class="fb-n"><a href="/DHAKA-FLIX-7/English%20Movies/%282024%29%201080p/">(2024) 1080p</a></td><td class="fb-d">2024-06-01 10:00</td><td></td></tr>
<tr><td></td><td class="fb-n"><a href="/DHAKA-FLIX-7/English%20Movies/%282023%29%201080p/">(2023) 1080p</a></td><td class="fb-d">2023-06-01 10:00</td><td></td></tr>
</table></body></html>"""
f(f"{em}/index.html", html.encode())
tv = f"{root}/DHAKA-FLIX-12/TV-WEB-Series"
for s in (1, 2):
    for e in range(1, 11):
        f(f"{tv}/TV Series ★ A — L/Breaking Bad (TV Series 2008–2013) 720p/Season {s}/Breaking.Bad.S0{s}E{e:02d}.720p.mkv")
f(f"{tv}/TV Series ★ A — L/Breaking Bad (TV Series 2008–2013) 720p/Breaking Bad.jpg")
for e in range(1, 4):
    f(f"{tv}/TV Series ★ A — L/Dark (2017)/Dark S01 1080p/Dark.S01E0{e}.mkv")
f(f"{tv}/TV Series ★ A — L/Dark (2017)/Dark S01 1080p/Subs/Dark.S01E01.eng.srt")
an = f"{root}/DHAKA-FLIX-10/Anime & Cartoon TV Series"
for e in [1, 2, 10, 11, 3]:
    f(f"{an}/Frieren (2023)/Frieren - Episode {e}.mkv")
print("ok")
