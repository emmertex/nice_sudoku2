#!/usr/bin/env python3
"""Export every app icon from favicon.svg. Requires rsvg-convert and Pillow."""
from io import BytesIO
from pathlib import Path
import subprocess
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'web/src/jsMain/resources'
SOURCE = RESOURCES / 'favicon.svg'


def render(size):
    png = subprocess.check_output(['rsvg-convert', '-w', str(size), '-h', str(size), str(SOURCE)])
    return Image.open(BytesIO(png)).convert('RGBA')


def main():
    for size, name in [(180, 'apple-touch-icon.png'), (192, 'icon-192.png'), (512, 'icon-512.png')]:
        render(size).save(RESOURCES / name)
    render(256).save(RESOURCES / 'favicon.ico', sizes=[(16, 16), (32, 32), (48, 48)])
    # Entire mark fits within the central 80% safe-zone circle, with opaque bleed.
    maskable = Image.new('RGBA', (512, 512), '#102F32')
    mark = render(280)
    maskable.alpha_composite(mark, (116, 116))
    maskable.convert('RGB').save(RESOURCES / 'icon-maskable-512.png')
    print('Generated SVG-derived PNG, ICO, and maskable icons.')


if __name__ == '__main__':
    main()
