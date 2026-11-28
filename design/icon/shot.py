import sys, asyncio
from playwright.async_api import async_playwright
async def main(svgs):
    async with async_playwright() as p:
        b=await p.chromium.launch(executable_path='/usr/bin/google-chrome')
        pg=await b.new_page(viewport={'width':1024,'height':1024})
        for s in svgs:
            await pg.set_content(f'<html><body style="margin:0;background:transparent">{open(s).read()}</body></html>')
            await pg.wait_for_timeout(300)
            await pg.screenshot(path=s.replace('.svg','.png'), omit_background=True, clip={'x':0,'y':0,'width':1024,'height':1024})
        await b.close()
asyncio.run(main(sys.argv[1:]))
